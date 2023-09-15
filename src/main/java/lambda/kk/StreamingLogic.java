package lambda.kk;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.HttpMethod;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import lambda.kk.codes.BlackFlag;
import lambda.kk.codes.VideoCutKindCode;
import lambda.kk.dto.CoverHlsFileToS3Request;
import lambda.kk.dummylocal.APIGatewayProxyResponseEvent;

/**
 * HLS変換処理
 *
 */
public class StreamingLogic
{

  /** ロガー. */
  private static final Logger logger = LoggerFactory.getLogger(StreamingLogic.class);
  // 定数
  /** ステータスコード:リクエスト不正. */
  private static final int HTTPSTATUSCODE_BAD_REQUEST = 400;
  /** ステータスコード:サーバーエラー. */
  private static final int HTTPSTATUSCODE_INTERNAL_SERVER_ERROR = 500;
  /** ステータスコード:リダイレクト. */
  private static final int HTTPSTATUSCODE_REDIRECT = 301;

  /** 正規表現:ファイル名抽出用. */
  private static final String DIR_REGEXP = "^.*/";
  /** 文字列 通常パターンts拡張子. */
  private static final String EXTENSION_TS = ".ts";
  /** 文字列 カットあり拡張子. */
  private static final String CUTED_EXTENSION_TS = "cuted.ts";
  /** 文字列 UTF8. */
  private static final String UTF8 = "UTF-8";
  /** 文字列 null. */
  private static final String STRING_NULL = "null";
  /** 文字列 Location. */
  private static final String STRING_LOCATION = "Location";

  //記号
  /** 半角アンド. */
  private static final String STRING_SYMBOL_AND = "&";
  /** 半角コロン. */
  private static final String STRING_SYMBOL_COLON = ":";
  /** 半角スラッシュ. */
  private static final String STRING_SYMBOL_SLASH = "/";
  /** 半角イコール. */
  private static final String STRING_SYMBOL_EQUALS = "=";
  /** 半角ハイフォン. */
  private static final String STRING_SYMBOL_HALF_HYPHEN = "-";
  /** 空文字. */
  private static final String STRING_EMPTY = "";
  /** 半角スペース. */
  private static final String STRING_HALF_SPACE = " ";

  // 環境変数Key
  /** 作業ベースディレクトリ. */
  private static final String EV_KEY_OUTUT_DIR = "tempWorkDir";
  /** 配信S3パケット名. */
  private static final String EV_KEY_BUCKET_NAME_KEY_LIVE = "bucketNameLive";
  /** 配信ファイルストレージクラス. */
  private static final String EV_KEY_PUT_S3_OBJECT_STORAGE_CLASS = "putS3ObjectStorageClass";
  /** ffmpegコマンドフォーマット変換テンプレート. */
  private static final String EV_KEY_FFMPEG_CMD_HLS_TEMPLET = "ffmpegCmdHlsTemplet";
  /** ffmpegコマンド後方カットテンプレート. */
  private static final String EV_KEY_FFMPEG_CMD_BACKWARD_CUT_TEMPLET = "ffmpegCmdBackwardCutTemplet";
  /** ffmpegコマンド前方カットテンプレート. */
  private static final String EV_KEY_FFMPEG_CMD_FRONTWARD_CUT_TEMPLET = "ffmpegCmdFrontwardCutTemplet";
  /** ffmpegコマンド実行時間制限(ミリ秒). */
  private static final String EV_KEY_FFMPEG_CMD_TIMELIMIT_MS = "ffmpegCmdTimeLimitMs";
  /** ワンタイムURL有効期限期限(ミリ秒). */
  private static final String EV_KEY_PRESIGNED_URL_TIME_LIMIT_MS = "presignedUrlTimeLimitMs";
  /** 黒画像ベースパス. */
  private static final String EV_KEY_BLACK_FILES_BASE_PATH = "blackFilesBasePath";
  /** 配信S3オブジェクトキー接頭辞.  */
  private static final String EV_KEY_LIVE_S3_OBJECT_KEY_PREFIX = "liveS3ObjectKeyPrefix";
  /** 黒画像ベースパス. */
  private static final String EV_KEY_RETRY_CUT_SECONDS = "retryCutSeconds";
  /** 暗号化共通鍵. */
  private static final String EV_KEY_ENCODE_COMMON_KEY = "encodeCommonKey";
  /** 暗号化ベクター. */
  private static final String EV_KEY_ENCODE_COMMON_VECTOR = "encodeCommonVector";

  public APIGatewayProxyResponseEvent handleRequest(Map<String, Object> eventInput, Context context) {

    URL responseUrl = null;

    Date checkStarSysTime = CommonUtils.toDate(CommonUtils.getSystemDateTime());
    CoverHlsFileToS3Request request = this.setupParam(eventInput);
    // 復号判定
    if (request == null) {
      // nullの場合処理を異常終了する。
      logger.error("復号判定:失敗");
      return this.createErrorRedirectResponse(HTTPSTATUSCODE_BAD_REQUEST);
    }

    try {
      // バリエーションチェック処理
      if (this.checkVariation(request)) {
        logger.error("variationチェック処理:失敗");
        return this.createErrorRedirectResponse(HTTPSTATUSCODE_BAD_REQUEST);
      }

      // 業務チェック処理
      if (this.checkBusiness(request)) {
        logger.error("businessチェック処理:失敗");
        return this.createErrorRedirectResponse(HTTPSTATUSCODE_BAD_REQUEST);
      }

    } catch (Exception e) {
      logger.error("チェック処理:失敗");
      return this.createErrorRedirectResponse(HTTPSTATUSCODE_BAD_REQUEST);
    }

    this.debugLog("チェック処理:処理時間", checkStarSysTime);
    try {

      // 変換処理
      responseUrl = this.mianProcess(request, context);

    } catch (Exception e) {

      logger.error("変換処理:失敗" + CommonUtils.getErrorMsg(e));
      return this.createErrorRedirectResponse(HTTPSTATUSCODE_INTERNAL_SERVER_ERROR);
    }
    //リダイレクト
    return this.createSuccessRedirectResponse(responseUrl, HTTPSTATUSCODE_REDIRECT);
  }

  /** パラメータセットアップ.
   * @param eventInput 入力情報
   * @return リクエストオブジェクト
   * */
  private CoverHlsFileToS3Request setupParam(Map<String, Object> eventInput) {

    try {
      // 蓄積画像配信リクエスト受信
      String encryptionedParamString = (String) eventInput.get("rawQueryString");

      // リクエスト復号
      String decodeParam = CommonUtils.decode(encryptionedParamString,
          (String) System.getenv(EV_KEY_ENCODE_COMMON_KEY),
          (String) System.getenv(EV_KEY_ENCODE_COMMON_VECTOR),
          UTF8);

      // key,valueペア単位分割
      String[] paramsString = decodeParam.split(STRING_SYMBOL_AND);
      Map<String, String> keyValueMap = new HashMap<String, String>();
      for (String param : paramsString) {

        // key,value取り出し、Mapにセット
        String[] splitted = param.split(STRING_SYMBOL_EQUALS);
        String key = splitted[0];
        String value = null;
        if (splitted.length == 2) {
          value = splitted[1];
        }

        keyValueMap.put(key, value);
      }

      // リクエストオブジェクトに変換
      ObjectMapper mapper = new ObjectMapper();
      CoverHlsFileToS3Request request = mapper.convertValue(keyValueMap, CoverHlsFileToS3Request.class);
      logger.info("decryptionRequest:" + request.toString());
      return request;
    } catch (Exception e) {
      logger.error("復号判定:失敗" + CommonUtils.getErrorMsg(e));
      return null;
    }

  }

  /** バリエーションチェック処理.
   * @param request リクエスト
   * @return true:エラーあり, false:エラー無し
   * @throws ParseException
   * */
  private boolean checkVariation(CoverHlsFileToS3Request request) throws ParseException {

    boolean errorFlg = false;

    // 共通必須:黒動画フラグ,制限時間
    if ((request.getBlackFlg() == null)
        || CommonUtils.stringIsBlank(request.getActiveLimit())) {

      return true;
    }

    int blackFlg = request.getBlackFlg().intValue();
    if (BlackFlag.GET_BLACK_FILE.getCode() == blackFlg) {
      // パターン黒動画: 黒動画ファイル名
      errorFlg = CommonUtils.stringIsBlank(request.getBlackFileName());
    } else {
      // パターン通常:ファイルパス、カット種別コード

      if ((request.getVideoCutKindCode() == null)
          || CommonUtils.stringIsBlank(request.getPath())) {
        return true;
      }
      // パターンカットあり:映像時間
      if (request.getVideoCutKindCode().intValue() != VideoCutKindCode.NO_CUT.getCode()) {
        return request.getDuration() == null;
      }
    }

    return errorFlg;
  }

  /** 業務チェック処理.
   * @param request リクエスト
   * @return true:エラーあり, false:エラー無し
   * */
  private boolean checkBusiness(CoverHlsFileToS3Request request) throws ParseException {

    Date sysDateTime = CommonUtils.toDate(CommonUtils.getSystemDateTime());
    Date activeLimitDateTime = CommonUtils.coverStringToDate(request.getActiveLimit());
    return sysDateTime.after(activeLimitDateTime);
  }

  /** 主処理.
   * @param request リクエスト
   * @param context aws固有情報
   * @return oneTimeUrl
   * */
  private URL mianProcess(CoverHlsFileToS3Request request, Context context)
      throws Exception {

    // ffmpegコマンドの実行タイムアウト制限(ミリ秒)
    int ffmCmdTimeOutLimitMs = Integer.valueOf(System.getenv(EV_KEY_FFMPEG_CMD_TIMELIMIT_MS));

    // ユニークキー作成
    String unikKey = this.getUnikKey(request);

    // 作業フォルダ初期化
    String tmpWorktDir = this.initDir(request, unikKey);

    // 主処理
    // S3クライアントの生成
    AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(Regions.AP_NORTHEAST_1).build();

    // ワンタイムURL発行対処Tsファイルフルパス
    String upLoadTsPath = STRING_EMPTY;

    if (BlackFlag.GET_BLACK_FILE.getCode() == request.getBlackFlg().intValue()) {

      // 黒い画面の場合S3のファイルダウンロード不要
      upLoadTsPath = this.getBlackFile(request);

    } else {
      Date s3DownLoadStarSysTime = CommonUtils.toDate(CommonUtils.getSystemDateTime());

      // S3ファイル取得
      File localAviFile = this.downloadS3FileToTmp(s3Client, request.getBucket(), request.getPath(), tmpWorktDir);

      this.debugLog("S3ダウンロード処理:処理時間", s3DownLoadStarSysTime);

      Date ffmpegCmdStarSysTime = CommonUtils.toDate(CommonUtils.getSystemDateTime());

      upLoadTsPath = this.exeFormatConversion(localAviFile, ffmCmdTimeOutLimitMs);

      int videoCutKindCode = request.getVideoCutKindCode();

      if (request.getVideoCutKindCode() != VideoCutKindCode.NO_CUT.getCode()) {

        int duration = CommonUtils.coverIntegerToInt(request.getDuration());

        upLoadTsPath = this.exeVideoCut(videoCutKindCode, duration, ffmCmdTimeOutLimitMs, false,
            tmpWorktDir, request);

        File cutedFile = new File(upLoadTsPath);

        if (cutedFile.length() == 0) {
          cutedFile.delete();
          upLoadTsPath = this.exeVideoCut(videoCutKindCode, duration, ffmCmdTimeOutLimitMs, true,
              tmpWorktDir, request);
        }
      }

      this.debugLog("画像変換処理:処理時間", ffmpegCmdStarSysTime);
    }

    Date s3UpLoadStarSysTime = CommonUtils.toDate(CommonUtils.getSystemDateTime());

    // 変換済みファイルアップロード
    URL oneTimeRul = this.uploadTsFileToS3(s3Client, unikKey, upLoadTsPath);
    this.debugLog("S3アップロード処理:処理時間", s3UpLoadStarSysTime);

    // 7.作業フォルダクリーン
    FileUtils.deleteDirectory(new File(tmpWorktDir));

    return oneTimeRul;

  }

  /** 黒画像取得.
   * @param request リクエスト
   * @return 黒画像パス
   * */
  private String getBlackFile(CoverHlsFileToS3Request request) {
    // 環境変数.画像ベースパス
    return System.getenv(EV_KEY_BLACK_FILES_BASE_PATH) + request.getBlackFileName();
  }

  /** フォーマット変換.
   * @param localAviFile AVIファイル
   * @param ffmCmdTimeOutLimitMs タイムアウト制限
   * @return tsパス
   * */
  private String exeFormatConversion(File localAviFile, int ffmCmdTimeOutLimitMs)
      throws IOException, InterruptedException {
    String outputFileFullPath = this.replaceFileNameEnd(localAviFile.getPath(), 4, EXTENSION_TS);

    // 画像フォーマット変換
    this.execShellCommand(
        this.editFfmpegFormatConversionCmd(localAviFile.getPath(), outputFileFullPath), ffmCmdTimeOutLimitMs);

    return outputFileFullPath;
  }

  /** 画像カット.
  * @param videoCutKindCode 画像カット種別
  * @param duration 映像時間
  * @param coveredTsFilePath 変換済みtsファイル
  * @param ffmCmdTimeOutLimitMs 実行タイムアウト制限
  * @param retryFlg リトライフラグ
  * @param tmpWorktDir ワークディレクトリ
  * @param request リクエスト
  * @return 出力ファイルパス
  * */
  private String exeVideoCut(int videoCutKindCode, int duration, int ffmCmdTimeOutLimitMs,
      boolean retryFlg, String tmpWorktDir, CoverHlsFileToS3Request request)
      throws IOException, InterruptedException {

    // ffmpeg入力ファイル
    String inputFilePah = tmpWorktDir
        + this.replaceFileNameEnd(this.coverS3ObjectKeytoFileName(request.getPath()), 4, EXTENSION_TS);

    //ffmpeg 出力ファイル名の編集
    String outputTsPath = this.replaceFileNameEnd(inputFilePah, 3, CUTED_EXTENSION_TS);

    String baseCutCmdTemplet = STRING_EMPTY;

    int optionValueint = 0;

    int trySeconds = Integer.valueOf(System.getenv(EV_KEY_RETRY_CUT_SECONDS)).intValue();

    if (VideoCutKindCode.FRONT_CUT.getCode() == videoCutKindCode) {
      // 環境変数.前方カット
      baseCutCmdTemplet = System.getenv(EV_KEY_FFMPEG_CMD_FRONTWARD_CUT_TEMPLET);
      // リトライの場合、60 - 映像時間-1秒
      optionValueint = retryFlg ? (60 - duration - trySeconds) : (60 - duration);
    } else if (VideoCutKindCode.BACK_CUT.getCode() == videoCutKindCode) {
      // 環境変数.後方カット
      baseCutCmdTemplet = System.getenv(EV_KEY_FFMPEG_CMD_BACKWARD_CUT_TEMPLET);
      // リトライの場合、映像時間 +1秒
      optionValueint = retryFlg ? duration + trySeconds : duration;
    }

    String cmd = MessageFormat.format(baseCutCmdTemplet,
        String.valueOf(optionValueint),
        inputFilePah,
        outputTsPath);

    this.execShellCommand(cmd, ffmCmdTimeOutLimitMs);

    return outputTsPath;
  }

  /**  Ffmpegコマンド編集(フォーマット変換用).
  * @param localAviFilePath 入力ファイルパス
  * @param outputTsPath 出力ファイルパス
  * @return 編集後コマンド
  * */
  private String editFfmpegFormatConversionCmd(String localAviFilePath, String outputTsPath) {
    // hls変換コマンドテンプレート
    String hlsCmdTemplet = System.getenv(EV_KEY_FFMPEG_CMD_HLS_TEMPLET);
    return MessageFormat.format(hlsCmdTemplet, localAviFilePath, outputTsPath);
  }

  /** ファイルダウンロード.
   * @param s3Client S3クライアント
   * @param bucket 蓄積パケット名
   * @param keyName S3オブジェクトキー
   * @param tmpWorktDir ワークディレクトリ
   * @return S3からダウンロードした入力ファイル
   * */
  private File downloadS3FileToTmp(AmazonS3 s3Client, String bucket, String s3ObjectKey, String tmpWorktDir) {

    GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, s3ObjectKey);

    File ouputFile = new File(tmpWorktDir + this.coverS3ObjectKeytoFileName(s3ObjectKey));

    s3Client.getObject(getObjectRequest, ouputFile);

    return ouputFile;
  }

  /** Tsファイルアップロード(配信用パケット).
   * @param s3Client S3クライアント
   * @param targetFilePath 変更済みファイル
   * @param putS3ObjectStorageClass ストレージクラス
   * */
  private URL uploadTsFileToS3(AmazonS3 s3Client, String unikKey, String targetFilePath) {

    // 環境変数.配信パケット名
    String bucketNameLive = System.getenv(EV_KEY_BUCKET_NAME_KEY_LIVE);

    File targetFile = new File(targetFilePath);

    String newKeyName = System.getenv(EV_KEY_LIVE_S3_OBJECT_KEY_PREFIX) + unikKey + STRING_SYMBOL_SLASH
        + targetFile.getName();

    PutObjectRequest putObjectRequest = new PutObjectRequest(bucketNameLive, newKeyName, targetFile);

    // 環境変数.S3オブジェクトのストレージクラス
    String putS3ObjectStorageClass = System.getenv(EV_KEY_PUT_S3_OBJECT_STORAGE_CLASS);

    // ストレージクラス
    putObjectRequest.withStorageClass(putS3ObjectStorageClass);

    s3Client.putObject(putObjectRequest);

    // 環境変数.ﾜﾝﾀｲﾑURL有効期限
    long presignedUrlTimeLimitMs = Long.valueOf(System.getenv(EV_KEY_PRESIGNED_URL_TIME_LIMIT_MS));
    // 有効期限の設定
    Date sysDatetime = CommonUtils.toDate(CommonUtils.getSystemDateTime());
    Date expiration = new Date(sysDatetime.getTime() + presignedUrlTimeLimitMs);
    long msec = expiration.getTime();
    msec += presignedUrlTimeLimitMs;
    expiration.setTime(msec);

    // 署名付きURLを生成するためのオプション設定
    // バケットとオブジェクトキーのセット
    GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketNameLive,
        newKeyName);

    // リクエストメソッド
    generatePresignedUrlRequest.setMethod(HttpMethod.GET);

    // 有効期限のセット
    generatePresignedUrlRequest.setExpiration(expiration);

    // 生成
    URL presignedUrl = s3Client.generatePresignedUrl(generatePresignedUrlRequest);

    return presignedUrl;
  }

  /**
   * 作業フォルダ初期化.
   * @param request リクエスト
   * @param unikKey ユニークキー
   */
  private String initDir(CoverHlsFileToS3Request request, String unikKey)
      throws IOException, InterruptedException {

    String tmpWorktDir = System.getenv(EV_KEY_OUTUT_DIR) + unikKey + STRING_SYMBOL_SLASH;

    FileUtils.deleteDirectory(new File(tmpWorktDir));
    new File(tmpWorktDir).mkdirs();
    return tmpWorktDir;
  }

  /**
   * S3オブジェクトキーからファイル名算出.
   * @param s3ObjectKey S3オブジェクトキー
   */
  private String coverS3ObjectKeytoFileName(String s3ObjectKey) {

    return s3ObjectKey.replaceAll(DIR_REGEXP, STRING_EMPTY);
  }

  /**
   * 外部コマンド実行.
   * @param cmd コマンド
   * @param timeoutLimit タイムアウト制限
   */
  private void execShellCommand(String cmd, int timeoutLimit)
      throws IOException,
      InterruptedException {
    CommandLine cmdLine = CommandLine.parse(cmd);
    ByteArrayOutputStream sStream = new ByteArrayOutputStream();
    ByteArrayOutputStream eStream = new ByteArrayOutputStream();

    PumpStreamHandler streamHandler = new PumpStreamHandler(sStream, eStream);

    //Executorを作成
    DefaultExecutor executor = new DefaultExecutor();
    executor.setStreamHandler(streamHandler);
    executor.setExitValue(0);
    //タイムアウト時間のセット
    ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutLimit);
    executor.setWatchdog(watchdog);
    //execute
    executor.execute(cmdLine);

  }

  /** ユニークキー作成.
  * @param request リクエスト
  * @return ユニークキー
  * */
  private String getUnikKey(CoverHlsFileToS3Request request) {
    return String.join(STRING_SYMBOL_HALF_HYPHEN,
        request.getLnDev(),
        request.getShotSttTstm(),
        CommonUtils.coverIntegerToString(request.getDuration()),
        request.getBucket(),
        request.getPath(),
        request.getResolution(),
        request.getFrameRate(),
        CommonUtils.coverIntegerToString(request.getVideoCutKindCode()),
        CommonUtils.coverIntegerToString(request.getBlackFlg()),
        request.getBlackFileName())
        .replaceAll(STRING_HALF_SPACE, STRING_EMPTY)
        .replaceAll(STRING_SYMBOL_SLASH, STRING_EMPTY)
        .replaceAll(STRING_SYMBOL_COLON, STRING_EMPTY)
        .replaceAll(STRING_NULL, STRING_EMPTY);
  }

  /** ファイル名後方編集.
  * @param filePath ファイルフルパス
  * @param length カット文字数
  * @param newString カット後追加文字s列
  * @return 編集後ファイル名
  * */
  private String replaceFileNameEnd(String filePath, int length, String newString) {
    return filePath.substring(0, filePath.length() - length) + newString;
  }

  /** 成功レスポンス編集.
  * @param oneTimeUrl URL
  * @param status ステータスコード
  * @return 成功レスポンス
  * */
  private APIGatewayProxyResponseEvent createSuccessRedirectResponse(URL oneTimeUrl, int status) {

    APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
    response.setStatusCode(status);
    response.setHeaders(Collections.singletonMap(STRING_LOCATION, oneTimeUrl.toExternalForm()));
    return response;
  }

  /** エラーレスポンス編集.
   * 
  * @param status ステータスコード
  * @return エラーレスポンス
  * */
  private APIGatewayProxyResponseEvent createErrorRedirectResponse(int status) {

    APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
    response.setStatusCode(status);
    response.setHeaders(Collections.singletonMap(STRING_LOCATION, null));
    return response;
  }

  /** デバッグログ.
   * 
  * @param proName メッセージ文字列
  * @param startTime 開始時間
  * */
  private void debugLog(String proName, Date start) {
    Date end = CommonUtils.toDate(CommonUtils.getSystemDateTime());
    logger.debug(proName + (end.getTime() - start.getTime()));

  }
}
