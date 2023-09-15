package lambda.kk;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import lambda.kk.codes.DownloadProcessErrorCode;
import lambda.kk.dto.DownLoadInputAviInfo;
import lambda.kk.dto.DownloadAviGetOneTimeUrlResponse;
import lambda.kk.dto.DownloadAviGetOneTimeUrlReuqest;
import lambda.kk.dto.InitProcessOutPut;

/**
 * 画像ダウンロード変換処理(VPC外)．
 *
 */
public class DownloadAviAssembledLogic
    implements RequestHandler<Map<String, Object>, Object>, Constant {

  /** ロガー. */
  private static final Logger logger = LoggerFactory.getLogger(DownloadAviAssembledLogic.class);

  private static final String LAMBDA_BASE_DIR = "/tmp/work";

  @Override
  public Object handleRequest(Map<String, Object> eventInput, Context context) {

    // ロジック初期化
    DownloadCommonLogic commonLogic = new DownloadCommonLogic(logger, LAMBDA_BASE_DIR);

    DownloadAviGetOneTimeUrlReuqest request = null;
    try {
      // リクエストオブジェクト変換
      request = commonLogic.coverStateInput(eventInput);
    } catch (Exception e) {
      return commonLogic.createErrorResponse(DownloadProcessErrorCode.INPUT_COVER_ERROR, e);
    }

    // バリエーションチェック
    boolean errorFlg = commonLogic.inputVariationCheck(request);

    if (errorFlg) {

      // バリエーションチェックエラー
      return commonLogic.createErrorResponse(DownloadProcessErrorCode.INPUT_CHECK_ERROR, null);
    }

    // 作業パス
    String workDir = null;
    try {
      // 作業パス初期化
      workDir = commonLogic.initWorkDir();

    } catch (Exception e) {

      return commonLogic.createErrorResponse(DownloadProcessErrorCode.INIT_WORK_FOLDER_ERROR, e);
    }

    // inputtext作成
    String ffmpegCmdInputTextFilePath = null;
    try {

      ffmpegCmdInputTextFilePath = commonLogic.createFfmpegCmdInputTextFile(workDir, request);

    } catch (Exception e) {

      return commonLogic.createErrorResponse(DownloadProcessErrorCode.CREATE_INTERMEDIATE_TEXT_FILE_ERROR, e);
    }

    // 中間変数をリクエストオブジェクトに設定
    request.setInitProcessResultJson(new InitProcessOutPut(workDir, ffmpegCmdInputTextFilePath, null));

    // ファイルリスト
    List<DownLoadInputAviInfo> targetList = request.getFileList();

    // 中間画像編集(基準値算出→ダウンロード→リサイズ)
    DownloadAviGetOneTimeUrlResponse errorResponse = commonLogic.downLoadEditCuteAviFile(eventInput, request,
        targetList);

    // 中間画像編集エラー判定
    if (Objects.nonNull(errorResponse)) {
      return errorResponse;
    }

    // 画像結合→ putS3→Create URL→作業フォルダ削除
    // 画像結合
    String outputAviFileFullPath = null;

    try {
      outputAviFileFullPath = commonLogic.joinAviFiles(eventInput, request);
    } catch (Exception e) {
      return commonLogic.createErrorResponse(DownloadProcessErrorCode.COMBINE_IMAGES_ERROR, e);
    }

    String s3ObjectKey = null;

    try {
      // 変換済みファイルアップロード
      s3ObjectKey = commonLogic.uploadFileToS3(eventInput, request, outputAviFileFullPath);
    } catch (Exception e) {
      return commonLogic.createErrorResponse(DownloadProcessErrorCode.UPLOAD_TO_S3_ERROR, e);
    }

    String oneTimeUrl = null;

    try {
      oneTimeUrl = commonLogic.createOneTimeUrl(s3ObjectKey);
    } catch (Exception e) {
      return commonLogic.createErrorResponse(DownloadProcessErrorCode.GENERATE_ONE_TIME_URL_ERROR, e);
    }

    try {
      commonLogic.deleteDirectory(request.getInitProcessResultJson().getWorkDir());
    } catch (Exception e) {
      return commonLogic.createErrorResponse(DownloadProcessErrorCode.CLEAN_WORK_FOLDER_ERROR, e);
    }
    return commonLogic.createSuccessResponse(oneTimeUrl);

  }

}
