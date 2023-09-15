package lambda.kk;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.util.CollectionUtils;

import lambda.kk.codes.DownloadProcessErrorCode;
import lambda.kk.dto.DownLoadInputAviInfo;
import lambda.kk.dto.DownloadAviGetOneTimeUrlResponse;
import lambda.kk.dto.DownloadAviGetOneTimeUrlReuqest;

/**
 * 画像ダウンロード変換処理(VPC内)．
 *
 */
public class DownloadAviSplitLogic
    implements RequestHandler<Map<String, Object>, Object>, Constant {

  /** 処理モード 初期. */
  private static final String PROCESS_MODE_INIT = "00";

  /** 処理モード 中間編集. */
  private static final String PROCESS_MODE_EDIT = "01";

  /** 処理モード 画像結合.*/
  private static final String PROCESS_MODE_JOIN = "02";

  /** 処理モード キー名. */
  private static final String PROCESS_MODE_KEY_NAME = "DownLoadEditTaskMode";

  /** stepfunction.引数キー名:処理対象リスト切り出し開始インデックス. */
  private static final String START_INDEX = "StartIndex";

  /** stepfunction.引数キー名:処理対象リスト切り出し終了インデックス. */
  private static final String END_INDEX = "EndIndex";

  /** ロガー. */
  private static final Logger logger = LoggerFactory.getLogger(DownloadAviSplitLogic.class);

  /** EFSベース作業フォルダ. */
  private static final String EFS_BASE_DIR = "/mnt/data";

  /***
   * @param eventInput イベント引数.
   * @param context コンテキスト(Aws固有情報).
   * @return Lambda処理結果
   * */
  @Override
  public Object handleRequest(Map<String, Object> eventInput, Context context) {

    // ロジック初期化
    DownloadCommonLogic commonLogic = new DownloadCommonLogic(logger, EFS_BASE_DIR);

    DownloadAviGetOneTimeUrlReuqest request = null;
    try {
      // リクエストオブジェクト変換
      request = commonLogic.coverStateInput(eventInput);
    } catch (Exception e) {
      return commonLogic.createInitModeErrorResponse(
          commonLogic.createErrorResponse(DownloadProcessErrorCode.INPUT_COVER_ERROR, e));
    }

    // バリエーションチェック
    boolean errorFlg = commonLogic.inputVariationCheck(request);

    if (errorFlg) {

      // バリエーションチェックエラー
      return commonLogic.createInitModeErrorResponse(
          commonLogic.createErrorResponse(DownloadProcessErrorCode.INPUT_CHECK_ERROR, null));
    }

    // 処理種別コード
    String processModeCode = (String) eventInput.get(PROCESS_MODE_KEY_NAME);

    if (PROCESS_MODE_INIT.equals(processModeCode)) {

      // 2時間前古い作業ディレクトリ削除
      commonLogic.delete2HoursOldFolders(EFS_BASE_DIR);

      // 作業ディレクトリ初期化→中間テキストファイル作成

      // 作業ディレクトリ
      String workDir = null;
      try {
        // 作業ディレクトリ初期化
        workDir = commonLogic.initWorkDir();

      } catch (Exception e) {

        return commonLogic.createInitModeErrorResponse(
            commonLogic.createErrorResponse(DownloadProcessErrorCode.INIT_WORK_FOLDER_ERROR, e));
      }

      // 中間テキストファイル
      String ffmpegCmdInputTextFilePath = null;

      try {

        // 中間テキストファイル作成
        ffmpegCmdInputTextFilePath = commonLogic.createFfmpegCmdInputTextFile(workDir, request);

      } catch (Exception e) {

        return commonLogic.createInitModeErrorResponse(
            commonLogic.createErrorResponse(DownloadProcessErrorCode.CREATE_INTERMEDIATE_TEXT_FILE_ERROR, e));
      }

      return commonLogic.createInitModeSuccessResponse(workDir, ffmpegCmdInputTextFilePath);

    } else if (PROCESS_MODE_EDIT.equals(processModeCode)) {

      // 前処理のエラー判定
      if (Objects.nonNull(request.getInitProcessResultJson().getProcessResponse().getErrorCode())) {
        return request.getInitProcessResultJson().getProcessResponse();
      }

      // リスト切り出し→中間画像編集(基準値算出→ダウンロード→リサイズ)
      int paramStartIndex = (int) eventInput.get(START_INDEX);
      int paramEndIndex = (int) eventInput.get(END_INDEX);
      // リスト切り出し
      List<DownLoadInputAviInfo> targetList = commonLogic.getTargetList(eventInput, request, paramStartIndex,
          paramEndIndex);

      if (CollectionUtils.isNullOrEmpty(targetList)) {
        // 空リストの場合
        return commonLogic.createSuccessResponse(null);
      }

      // 中間画像編集(基準値算出→ダウンロード→リサイズ)
      DownloadAviGetOneTimeUrlResponse errorResponse = commonLogic.downLoadEditCuteAviFile(eventInput, request,
          targetList);

      if (Objects.nonNull(errorResponse)) {

        return errorResponse;
      }
      DownloadAviGetOneTimeUrlResponse successResponse = commonLogic.createSuccessResponse(null);

      return successResponse;

    } else if (PROCESS_MODE_JOIN.equals(processModeCode)) {

      // 前処理のエラー判定
      for (DownloadAviGetOneTimeUrlResponse editProcessResult : request.getEditProcessesResultJson()) {
        if (StringUtils.isNoneBlank(editProcessResult.getErrorCode())) {

          // 作業フォルダ削除
          commonLogic.deleteDirectory(request.getInitProcessResultJson().getWorkDir());

          // 異常終了処理存在した場合は結合せず、エラーレスポンスを返す
          return editProcessResult;
        } else {
          // 正常終了の場合、何もしない。
        }
      }

      // 画像結合→ 変換済みファイルアップロード→URL発行→作業フォルダ削除

      // 出力ファイルフルパス
      String outputAviFileFullPath = null;
      try {
        // 画像結合
        outputAviFileFullPath = commonLogic.joinAviFiles(eventInput, request);
      } catch (Exception e) {
        // エラーが発生する場合 作業ディレクトリ削除
        commonLogic.deleteDirectory(request.getInitProcessResultJson().getWorkDir());
        return commonLogic.createErrorResponse(DownloadProcessErrorCode.COMBINE_IMAGES_ERROR, e);
      }

      String s3ObjectKey = null;
      try {
        // 変換済みファイルアップロード
        s3ObjectKey = commonLogic.uploadFileToS3(eventInput, request, outputAviFileFullPath);
      } catch (Exception e) {
        // エラーが発生する場合 作業ディレクトリ削除
        commonLogic.deleteDirectory(request.getInitProcessResultJson().getWorkDir());
        return commonLogic.createErrorResponse(DownloadProcessErrorCode.UPLOAD_TO_S3_ERROR, e);
      }

      String oneTimeUrl = null;

      try {
        // URL発行
        oneTimeUrl = commonLogic.createOneTimeUrl(s3ObjectKey);
      } catch (Exception e) {
        // エラーが発生する場合 作業ディレクトリ削除
        commonLogic.deleteDirectory(request.getInitProcessResultJson().getWorkDir());
        return commonLogic.createErrorResponse(DownloadProcessErrorCode.GENERATE_ONE_TIME_URL_ERROR, e);
      }

      try {
        // 作業フォルダ削除
        commonLogic.deleteDirectory(request.getInitProcessResultJson().getWorkDir());
      } catch (Exception e) {
        return commonLogic.createErrorResponse(DownloadProcessErrorCode.CLEAN_WORK_FOLDER_ERROR, e);
      }
      return commonLogic.createSuccessResponse(oneTimeUrl);

    }

    return null;

  }

}
