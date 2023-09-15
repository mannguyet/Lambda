package lambda.kk;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import com.amazonaws.HttpMethod;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import com.amazonaws.util.CollectionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import lambda.kk.codes.DownloadProcessErrorCode;
import lambda.kk.codes.VideoCutKindCode;
import lambda.kk.dto.DownLoadInputAviInfo;
import lambda.kk.dto.DownloadAviGetOneTimeUrlResponse;
import lambda.kk.dto.DownloadAviGetOneTimeUrlReuqest;
import lambda.kk.dto.InitProcessOutPut;

/**
 * 画像ダウンロード変換処理(共通)．
 *
 */
public class DownloadCommonLogic implements Constant {

  /** stepfunction.引数名.*/
  public static final String STEP_FUNCTION_PARAM_NAME = "SfnInput";

  /** パラメータストアサービス識別子.*/
  private static final String PARAMETERSTORE_SERVICE_NAME = "pm";

  /** aws システムマネージャーパラメータストア キー接頭辞.*/
  private String systemParameterStoreKeyPrefix = null;

  /** 環境コード.*/
  private static final String EV_KEY_PROPERTY_ENV = "Env";

  /** システム名.*/
  private static final String EV_KEY_PROPERTY_SYSTEMNAME = "SystemName";

  /** AWSリソース識別子.*/
  private static final String EV_KEY_PROPERTY_RESOURCENAME = "ResourceName";

  /** ffmpegコマンド実行時間制限(ミリ秒). */
  private static final String EV_KEY_FFMPEG_CMD_TIMELIMIT_MS = "ffmpegCmdTimeLimitMs";

  /** 平行実行するマルチスレッド数(最大).  */
  private static final String EV_KEY_MAX_THREAD_COUNT = "maxthreadCount";

  /** ffmpegコマンドフォーマットリサイズテンプレート. */
  private static final String EV_KEY_FFMPEG_CMD_FORMAT_UNIF_TEMPLET = "ffmpegCmdFormatUnificationTemplet";

  /** トライカット間隔秒数.  */
  private static final String EV_KEY_RETRY_CUT_SECONDS = "retryCutSeconds";

  /** ffmpegコマンド後方カットテンプレート. */
  private static final String EV_KEY_FFMPEG_CMD_BACKWARD_CUT_TEMPLET = "ffmpegCmdBackwardCutTemplet";

  /** ffmpegコマンド前方カットテンプレート. */
  private static final String EV_KEY_FFMPEG_CMD_FRONTWARD_CUT_TEMPLET = "ffmpegCmdFrontwardCutTemplet";

  /** ffmpegコマンドAVI結合テンプレート. */
  private static final String EV_KEY_FFMPEG_CMD_AVI_JOIN_TEMPLET = "ffmpegCmdAviJoinTemplet";

  /** 配信S3パケット名. */
  private static final String EV_KEY_BUCKET_NAME_KEY_LIVE = "bucketNameLive";

  /** 配信ファイルストレージクラス. */
  private static final String EV_KEY_PUT_S3_OBJECT_STORAGE_CLASS = "putS3ObjectStorageClass";

  /** 配信S3オブジェクトキー接頭辞.  */
  private static final String EV_KEY_LIVE_S3_OBJECT_KEY_PREFIX = "liveS3ObjectKeyPrefix";

  /** ワンタイムURL有効期限期限(ミリ秒). */
  private static final String EV_KEY_PRESIGNED_URL_TIME_LIMIT_MS = "presignedUrlTimeLimitMs";

  /**マルチパートアップロード基準ファイルサイズ. */
  private static final String EV_KEY_MULTIPART_UPLOAD_STANDARD_FILE_SIZE = "multipartUploadStandardFileSize";

  /** マルチパートアップロードブロックサイズ. */
  private static final String EV_KEY_MULTIPART_UPLOAD_BLOCK_SIZE = "multipartUploadBlockSize";

  // 一時変数
  /** 基準.解像度. */
  private String standardResolution = null;
  /** 基準.フレームレート. */
  private String standardFrameRate = null;
  /*** リサイズフラグ. */
  private boolean allResizeFlg = false;

  /*** ロガー. */
  private Logger logger = null;
  /*** ベース作業ディレクトリ. */
  private String baseWorkDir = null;

  /** コンストラクタ.*/
  public DownloadCommonLogic(Logger logger, String baseWorkDir) {

    this.logger = logger;
    this.baseWorkDir = baseWorkDir;
    this.systemParameterStoreKeyPrefix = (String) System.getenv(EV_KEY_PROPERTY_ENV)
        + Constant.STRING_HALF_HYPHEN
        + (String) System.getenv(EV_KEY_PROPERTY_SYSTEMNAME)
        + Constant.STRING_HALF_HYPHEN
        + PARAMETERSTORE_SERVICE_NAME
        + Constant.STRING_HALF_HYPHEN
        + (String) System.getenv(EV_KEY_PROPERTY_RESOURCENAME)
        + Constant.STRING_HALF_HYPHEN;
  }

  /** コンストラクタ.*/
  public DownloadAviGetOneTimeUrlReuqest coverStateInput(Map<String, Object> eventInput)
      throws Exception {
    DownloadAviGetOneTimeUrlReuqest request = null;
    // 入力変換
    request = this.setupParam(eventInput);

    return request;
  }

  /** パラメータセットアップ.
   * @param eventInput イベント引数.
   * @return リクエストオブジェクト
   * */
  @SuppressWarnings("unchecked")
  private DownloadAviGetOneTimeUrlReuqest setupParam(Map<String, Object> eventInput)
      throws Exception {

    Map<String, Object> paramMap = (Map<String, Object>) eventInput.get(STEP_FUNCTION_PARAM_NAME);

    ObjectMapper mapper = new ObjectMapper();
    DownloadAviGetOneTimeUrlReuqest request = mapper.convertValue(paramMap,
        DownloadAviGetOneTimeUrlReuqest.class);
    return request;

  }

  /** 入力ビジネスチェック.
   * @param request リクエストオブジェクト
   * @return true:エラーあり、false:エラー無
   * */
  public boolean inputVariationCheck(DownloadAviGetOneTimeUrlReuqest request) {
    // 0件チェック
    if (CollectionUtils.isNullOrEmpty(request.getFileList())) {
      return true;
    }

    for (DownLoadInputAviInfo aviInfo : request.getFileList()) {

      // ブランクチェック
      if (CommonUtils.stringIsBlank(aviInfo.getBucket())
          || CommonUtils.stringIsBlank(aviInfo.getObjectkey())
          || CommonUtils.stringIsBlank(aviInfo.getResolution())
          || CommonUtils.stringIsBlank(aviInfo.getFrameRate())
          || aviInfo.getVideoCutKindCode() == null
          || ((aviInfo.getVideoCutKindCode().intValue() != VideoCutKindCode.NO_CUT.getCode())
              && aviInfo.getDuration() == null)) {
        return true;
      }

      // 空白チェック
      if (aviInfo.getBucket().contains(STRING_HALF_SPACE)
          || aviInfo.getObjectkey().contains(STRING_HALF_SPACE)
          || aviInfo.getResolution().contains(STRING_HALF_SPACE)
          || aviInfo.getFrameRate().contains(STRING_HALF_SPACE)) {
        return true;
      }

      // 拡張子チェック
      if (!aviInfo.getObjectkey().endsWith(EXTENSION_AVI)) {
        return true;
      }
    }

    return false;
  }

  /////////////////////// モード① start////////////////////////////
  /** 作業ディレクトリ作成.
   * */
  public String initWorkDir() throws Exception {

    DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss_SSS_");
    LocalDateTime serverSysDate = CommonUtils.getSystemDateTime();

    String path = this.baseWorkDir + STRING_SYMBOL_SLASH + sdf.format(serverSysDate)
        + CommonUtils.generate(RANDOM_STRING_LENGTH)
        + STRING_SYMBOL_SLASH;

    File pathFileObj = new File(path);

    if (pathFileObj.exists()) {
      // 既存作業フォルダが存在の場合削除
      this.deleteDirectory(path);
    } else {
      // 何もしない。
    }
    pathFileObj.mkdirs();

    return path;
  }

  /**
   * 中間ファイル作成.
   * @param workDir 作業ディレクトリ
   * @param request リクエスト
   */
  public String createFfmpegCmdInputTextFile(String workDir,
      DownloadAviGetOneTimeUrlReuqest request) throws IOException {

    String ffmpegCmdInputTextFilePath = workDir + CMD_INPUT_TEXT_FILE_NAME;
    // 書込み用オブジェクトを生成する
    BufferedWriter bw = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(ffmpegCmdInputTextFilePath), StandardCharsets.UTF_8.name()));

    for (DownLoadInputAviInfo inputAviInfo : request.getFileList()) {
      bw.write("file "
          + workDir
          + this.coverS3ObjectKeytoFileName(inputAviInfo.getObjectkey())
          + System.getProperty("line.separator"));
    }

    //ファイルを閉じる
    bw.close();

    return ffmpegCmdInputTextFilePath;
  }

  /**
   * S3オブジェクトキーからファイル名算出.
   * @param s3ObjectKey S3オブジェクトキー
   * @return ファイル名
   */
  private String coverS3ObjectKeytoFileName(String s3ObjectKey) {

    return s3ObjectKey.replaceAll(DIR_REGEXP, STRING_EMPTY);
  }

  /**
   * 初期処理_処理結果作成(成功).
   * @param workDir 作業ディレクトリ.
   * @param inputTextPath 中間ファイルフルパス.
   * @return 初期処理_処理結果
   */
  public InitProcessOutPut createInitModeSuccessResponse(String workDir, String inputTextPath) {

    InitProcessOutPut oput = new InitProcessOutPut(
        workDir,
        inputTextPath,
        this.createSuccessResponse(null));

    oput.setWorkDir(workDir);
    oput.setInputTextPath(inputTextPath);

    return oput;
  }

  /**
   * 初期処理_処理結果作成(エラー).
   * @param errorResponse エラーレスポンスを.
   * @return 初期処理_処理結果
   */
  public InitProcessOutPut createInitModeErrorResponse(DownloadAviGetOneTimeUrlResponse errorResponse) {

    InitProcessOutPut oput = new InitProcessOutPut(errorResponse);

    return oput;
  }

  /////////////////////// モード① end////////////////////////////

  /////////////////////// モード② start////////////////////////////
  /**
   * 処理対象ファイルリスト切り出し.
   * @param  eventInput イベント引数.
   * @param  request リクエストオブジェクト.
   * @return 処理対象ファイルリスト.
   */
  public List<DownLoadInputAviInfo> getTargetList(Map<String, Object> eventInput,
      DownloadAviGetOneTimeUrlReuqest request, int paramStartIndex, int paramEndIndex) {

    List<DownLoadInputAviInfo> subList = new ArrayList<>();

    List<DownLoadInputAviInfo> inputList = request.getFileList();
    int listSize = inputList.size();

    // 範囲外の場合は空のリストを返す
    if (paramStartIndex >= listSize || paramEndIndex < 0 || paramStartIndex > paramEndIndex) {
      return subList;
    }

    // 範囲内の要素を取り出す
    for (int i = paramStartIndex; i <= paramEndIndex && i < listSize; i++) {
      DownLoadInputAviInfo element = inputList.get(i);
      if (element != null) {
        subList.add(element);
      }
    }

    return subList;
  }

  /**
   * 中間画像編集(基準値算出→ダウンロード→リサイズ).
   * @param eventInput イベント引数.
   * @param request リクエストオブジェクト.
   * @param targetList 処理対象ファイルリスト.
   * @return エラーレスポンス(中間画像編集).
   */
  public DownloadAviGetOneTimeUrlResponse downLoadEditCuteAviFile(Map<String, Object> eventInput,
      DownloadAviGetOneTimeUrlReuqest request, List<DownLoadInputAviInfo> targetList) {

    // 作業DIR
    String tmpWorktDir = request.getInitProcessResultJson().getWorkDir();

    // S3クライアントの生成
    AmazonS3 s3Client = this.initS3Cloud();

    // ssmクライアントの生成
    AWSSimpleSystemsManagement ssmClient = this.initSsmCloud();

    try {
      // 基準値算出
      this.calculateThresholdValue(request);
    } catch (Exception e) {
      return this.createErrorResponse(DownloadProcessErrorCode.AGGREGATE_STANDARD_IMAGE_INFORMATION_ERROR, e);
    }

    try {
      // S3ファイル取得
      this.downLoadAviFile(ssmClient, s3Client, targetList, tmpWorktDir);
    } catch (Exception e) {
      return this.createErrorResponse(DownloadProcessErrorCode.GET_S3_FILE_ERROR, e);
    }

    try {
      // 中間画像編集
      this.editMiddleVideo(ssmClient, tmpWorktDir, targetList);
    } catch (Exception e) {
      return this.createErrorResponse(DownloadProcessErrorCode.EDIT_INTERMEDIATE_IMAGE_ERROR, e);
    }

    return null;

  }

  /** 基準値算出.
  * @param request リクエストオブジェクト.
  *
  * */
  private void calculateThresholdValue(DownloadAviGetOneTimeUrlReuqest request) {

    List<String> valueList = request.getFileList().stream()
        .map(aviInfo -> String.join(STRING_COMMA_COLON, aviInfo.getResolution(), aviInfo.getFrameRate()))
        .collect(Collectors.toList());

    // マップの作成
    Map<String, Integer> map = new HashMap<String, Integer>();

    // リストの各要素をカウント
    for (String s : valueList) {
      Integer count = map.get(s);
      if (count == null) {
        count = 0;
      }
      map.put(s, count + 1);
    }

    // 最も多いパターンを算出
    int maxCount = 0;
    String maxElement = null;
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      if (entry.getValue() > maxCount) {
        maxCount = entry.getValue();
        maxElement = entry.getKey();
      }
    }

    String[] values = maxElement.split(STRING_COMMA_COLON);

    // メンバー変数に代入

    // 基準解像度
    standardResolution = values[0];

    // 基準フレームレート
    standardFrameRate = values[1];

    // 全体リサイズフラグ
    allResizeFlg = (map.keySet().size() > 1);

  }

  /**
   * S3ファイル取得.
   * @param ssmClient ssmクライアント.
   * @param s3Client S3クライアント.
   * @param targetList 処理対象リスト.
   * @param workDir 作業ディレクトリ.
   */
  private void downLoadAviFile(AWSSimpleSystemsManagement ssmClient, AmazonS3 s3Client,
      List<DownLoadInputAviInfo> targetList,
      String workDir) throws Exception {

    int maxThreadCount = Integer.valueOf(this.getSystemParam(ssmClient, EV_KEY_MAX_THREAD_COUNT)).intValue();

    Date s3DownLoadStarSysTime = CommonUtils.toDate(CommonUtils.getSystemDateTime());// TODO

    // ExecutorServiceを作成して、スレッドプールを生成
    ExecutorService executorService = Executors.newFixedThreadPool(maxThreadCount);

    // ダウンロードタスクを設定
    List<Future<?>> futures = new ArrayList<>();
    for (DownLoadInputAviInfo inputAviInfo : targetList) {
      futures.add(executorService.submit(() -> {

        this.downloadS3FileToTmp(s3Client, inputAviInfo.getBucket(), inputAviInfo.getObjectkey(), workDir);

      }));
    }

    // すべてのダウンロードタスクが完了するまで待機
    for (Future<?> future : futures) {
      future.get();
    }

    // ExecutorServiceを終了させる
    executorService.shutdown();

    this.debugLog("S3ダウンロード処理:処理時間(ms)", s3DownLoadStarSysTime);

  }

  /** ファイルダウンロード.
   * @param s3Client S3クライアント
   * @param bucketName パケット名
   * @param s3ObjectKey S3オブジェクトキー
   * @param worktDir 作業ディレクトリ
   * @return S3からダウンロードした入力ファイルパス
   * */
  private String downloadS3FileToTmp(AmazonS3 s3Client, String bucketName, String s3ObjectKey, String worktDir) {

    GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, s3ObjectKey);

    File ouputFile = new File(worktDir + this.coverS3ObjectKeytoFileName(s3ObjectKey));
    s3Client.getObject(getObjectRequest, ouputFile);
    return ouputFile.getAbsolutePath();
  }

  /**
   * 中間画像編集.
   * @param ssmClient ssmクライアント.
   * @param workDir 作業ディレクトリ.
   * @param targetList 処理対象リスト.
   */
  private void editMiddleVideo(AWSSimpleSystemsManagement ssmClient, String workDir,
      List<DownLoadInputAviInfo> targetList)
      throws Exception {

    // ffmpegコマンドの実行タイムアウト制限(ミリ秒)
    final int ffmCmdTimeOutLimitMs = Integer.valueOf(this.getSystemParam(ssmClient, EV_KEY_FFMPEG_CMD_TIMELIMIT_MS));

    final String resizeCmdTemplet = this.getSystemParam(ssmClient, EV_KEY_FFMPEG_CMD_FORMAT_UNIF_TEMPLET);

    Date resizeStarSysTime = CommonUtils.toDate(CommonUtils.getSystemDateTime());

    int maxThreadCount = Integer.valueOf(this.getSystemParam(ssmClient, EV_KEY_MAX_THREAD_COUNT)).intValue();

    // ExecutorServiceを作成して、スレッドプールを生成
    ExecutorService executorService = Executors.newFixedThreadPool(maxThreadCount);

    // 中間画像編集タスクを設定
    List<Future<?>> futures = new ArrayList<>();
    for (DownLoadInputAviInfo inputAviInfo : targetList) {
      futures.add(executorService.submit(() -> {

        try {
          this.editMiddleVideoDetail(ssmClient, workDir, inputAviInfo, ffmCmdTimeOutLimitMs, resizeCmdTemplet);
        } catch (Exception e) {
          // エラーが発生した場合は、他のタスクをキャンセルして、エラーをthrowする
          executorService.shutdownNow();
          new Exception(e);
        }

      }));
    }

    // すべての中間画像編集タスクが完了するまで待機
    for (Future<?> future : futures) {
      future.get();
    }

    // ExecutorServiceを終了させる
    executorService.shutdown();

    this.debugLog("中間画像編集:処理時間(ms)", resizeStarSysTime);
  }

  /**
   * 中間画像編集(詳細).
   * @param ssmClient ssmクライアント.
   * @param workDir 作業ディレクトリ.
   * @param inputAviInfo 画像情報.
   * @param ffmCmdTimeOutLimitMs ffmpegコマンドタイム制限.
   * @param resizeCmdTemplet コマンドリサイズテンプレート.
   */
  private void editMiddleVideoDetail(AWSSimpleSystemsManagement ssmClient, String workDir,
      DownLoadInputAviInfo inputAviInfo, int ffmCmdTimeOutLimitMs,
      String resizeCmdTemplet)
      throws Exception {

    String downloadedAviFilePath = new File(workDir + this.coverS3ObjectKeytoFileName(inputAviInfo.getObjectkey()))
        .getAbsolutePath();

    // リサイズ.
    this.resizeFormatUnification(inputAviInfo, downloadedAviFilePath, ffmCmdTimeOutLimitMs, resizeCmdTemplet);

    //画像カット判定
    if (VideoCutKindCode.NO_CUT.getCode() == inputAviInfo.getVideoCutKindCode()) {
      // カットしない場合は 何もしない。
    } else {

      String editedAviPath = this.exeVideoCut(ssmClient, ffmCmdTimeOutLimitMs, false, inputAviInfo,
          downloadedAviFilePath);

      if (new File(editedAviPath).length() == 0) {
        editedAviPath = this.exeVideoCut(ssmClient, ffmCmdTimeOutLimitMs, true, inputAviInfo, editedAviPath);
      }

    }

  }

  /** リサイズ.
   * @param aviInfo 入力AVIファイル情報
   * @param downloadedAviFilePath リサイズ対象ファイルフルパス
   * @param ffmCmdTimeOutLimitMs タイムアウト制限
   * @param resizeCmdTemplet コマンドリサイズテンプレート.
   * */
  private void resizeFormatUnification(DownLoadInputAviInfo aviInfo, String downloadedAviFilePath,
      int ffmCmdTimeOutLimitMs, String resizeCmdTemplet)
      throws Exception {

    // リサイズ判定
    if (this.allResizeFlg) {

      String outputFileFullPath = this.replaceFileNameEnd(downloadedAviFilePath, 4, FU_EXTENSION_AVI);

      String cmd = MessageFormat.format(resizeCmdTemplet, downloadedAviFilePath, this.standardResolution,
          this.standardFrameRate,
          outputFileFullPath);

      // ffmCmd 実行
      this.execShellCommand(cmd, ffmCmdTimeOutLimitMs);

      CommonUtils.rename(downloadedAviFilePath, outputFileFullPath);
      return;
    } else {
      // リサイズ対象対象外の場合 何もしない。
    }

  }

  /** 画像カット.
  * @param ssmClient ssmクライアント.
  * @param ffmCmdTimeOutLimitMs 実行タイムアウト制限.
  * @param retryFlg リトライフラグ.
  * @param inputAviInfo 入力AVIファイル情報.
  * @param request リクエスト.
  * @return 出力ファイルパス
  * */
  private String exeVideoCut(AWSSimpleSystemsManagement ssmClient, int ffmCmdTimeOutLimitMs,
      boolean retryFlg, DownLoadInputAviInfo inputAviInfo, String downloadEdAviFilePath)
      throws Exception {
    int videoCutKindCode = inputAviInfo.getVideoCutKindCode();
    int duration = inputAviInfo.getDuration();

    //ffmpeg 出力ファイル名の編集
    String tempOutputAviFullPath = this.replaceFileNameEnd(downloadEdAviFilePath, 4, CUTED_EXTENSION_AVI);

    String baseCutCmdTemplet = STRING_EMPTY;

    int optionValueint = 0;

    int trySeconds = Integer.valueOf(this.getSystemParam(ssmClient, EV_KEY_RETRY_CUT_SECONDS)).intValue();

    if (VideoCutKindCode.FRONT_CUT.getCode() == videoCutKindCode) {
      // 環境変数.前方カット
      baseCutCmdTemplet = this.getSystemParam(ssmClient, EV_KEY_FFMPEG_CMD_FRONTWARD_CUT_TEMPLET);
      // リトライの場合、60 - 映像時間-1秒
      optionValueint = retryFlg ? (60 - duration - trySeconds) : (60 - duration);
    } else if (VideoCutKindCode.BACK_CUT.getCode() == videoCutKindCode) {
      // 環境変数.後方カット
      baseCutCmdTemplet = this.getSystemParam(ssmClient, EV_KEY_FFMPEG_CMD_BACKWARD_CUT_TEMPLET);
      // リトライの場合、映像時間 +1秒
      optionValueint = retryFlg ? duration + trySeconds : duration;
    }

    String cmd = MessageFormat.format(baseCutCmdTemplet,
        String.valueOf(optionValueint),
        downloadEdAviFilePath,
        tempOutputAviFullPath);

    this.execShellCommand(cmd, ffmCmdTimeOutLimitMs);

    CommonUtils.rename(downloadEdAviFilePath, tempOutputAviFullPath);

    return downloadEdAviFilePath;

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
    this.logger.debug("コマンド実行:" + cmdLine);
    ByteArrayOutputStream successStream = new ByteArrayOutputStream();
    ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

    PumpStreamHandler streamHandler = new PumpStreamHandler(successStream, errorStream);

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

  /////////////////////// モード② end////////////////////////////

  /////////////////////// モード③ start////////////////////////////
  /** 画像結合.
   * @param eventInput イベント引数.
   * @param request リクエストオブジェクト.
   * @return 結合済み画像ファイルフルパス
   * */
  public String joinAviFiles(Map<String, Object> eventInput, DownloadAviGetOneTimeUrlReuqest request)
      throws Exception {

    // ssmクライアントの生成
    AWSSimpleSystemsManagement ssmClient = this.initSsmCloud();

    String ffmpegCmdInputTextFilePath = request.getInitProcessResultJson().getInputTextPath();

    String workDir = request.getInitProcessResultJson().getWorkDir();

    String outputAviFileName = CommonUtils.generate(RANDOM_STRING_LENGTH) + EXTENSION_AVI;

    String outputAviFilePath = workDir + outputAviFileName;

    Date ffmpegCmdStarSysTime = CommonUtils.toDate(CommonUtils.getSystemDateTime());

    String cmd = this.editFfmpegJoinCmd(ssmClient, ffmpegCmdInputTextFilePath, outputAviFilePath);

    // ffmpegコマンドの実行タイムアウト制限(ミリ秒)
    int ffmCmdTimeOutLimitMs = Integer.valueOf(this.getSystemParam(ssmClient, EV_KEY_FFMPEG_CMD_TIMELIMIT_MS));

    this.execShellCommand(cmd, ffmCmdTimeOutLimitMs);

    this.debugLog("画像結合処理:処理時間(ms)", ffmpegCmdStarSysTime);

    return outputAviFilePath;
  }

  /**  Ffmpegコマンド編集(AVI結合用).
  * @param ssmClient ssmクライアント
  * @param localAviFilePath 入力ファイルパス
  * @param outputTsPath 出力ファイルパス
  * @return 編集後コマンド
  * */
  private String editFfmpegJoinCmd(AWSSimpleSystemsManagement ssmClient, String requestTextFile,
      String outputFileFullPath) {
    // hls変換コマンドテンプレート

    String joinCmdTemplet = this.getSystemParam(ssmClient, EV_KEY_FFMPEG_CMD_AVI_JOIN_TEMPLET);

    return MessageFormat.format(joinCmdTemplet, requestTextFile, outputFileFullPath);
  }

  /** ユニークハッシュコード作成.
   * @param request リクエスト
   * @return ハッシュ値(SHA-256)
   * @throws Exception
   * */
  private String createUnicHashKey(DownloadAviGetOneTimeUrlReuqest request) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    return CommonUtils.hash(mapper.writeValueAsString(request).replaceAll(STRING_HALF_SPACE, STRING_EMPTY));

  }

  /** aviファイルアップロード(配信用パケット).
   * @param eventInput イベント引数.
   * @param request リクエストオブジェクト.
   * @param outputAviFileFullPath 変更済みファイル.
   * @throws Exception
   * @throws IOException
   * */
  public String uploadFileToS3(Map<String, Object> eventInput, DownloadAviGetOneTimeUrlReuqest request,
      String outputAviFileFullPath) throws Exception {

    // ssmクライアントの生成
    AWSSimpleSystemsManagement ssmClient = this.initSsmCloud();

    AmazonS3 s3Client = this.initS3Cloud();

    Date s3upLoadStarSysTime = CommonUtils.toDate(CommonUtils.getSystemDateTime());

    File targetFile = new File(outputAviFileFullPath);

    String bucketNameLive = this.getSystemParam(ssmClient, EV_KEY_BUCKET_NAME_KEY_LIVE);

    String liveS3ObjectKeyPrefix = this.getSystemParam(ssmClient, EV_KEY_LIVE_S3_OBJECT_KEY_PREFIX);

    String s3ObjectKey = liveS3ObjectKeyPrefix + this.createUnicHashKey(request) + EXTENSION_AVI;

    if (this.usMultipartUpload(ssmClient, outputAviFileFullPath)) {
      this.isUsingMultipartUpload(s3Client, ssmClient, outputAviFileFullPath, bucketNameLive, s3ObjectKey);
    } else {
      PutObjectRequest putObjectRequest = new PutObjectRequest(bucketNameLive, s3ObjectKey, targetFile);

      // 環境変数.S3オブジェクトのストレージクラス
      String putS3ObjectStorageClass = this.getSystemParam(ssmClient, EV_KEY_PUT_S3_OBJECT_STORAGE_CLASS);

      // ストレージクラス
      putObjectRequest.withStorageClass(putS3ObjectStorageClass);

      s3Client.putObject(putObjectRequest);
    }

    this.debugLog("S3アップロード処理:処理時間(ms)", s3upLoadStarSysTime);

    return s3ObjectKey;
  }

  /** マルチパートアップロード利用チェック.
   * @param eventInput eventInput イベント引数.
   * @param request リクエストオブジェクト.
   * @param outputAviFileFullPath 変更済みファイル.
   * */
  private boolean usMultipartUpload(AWSSimpleSystemsManagement ssmClient, String outputAviFileFullPath) {

    String maxFileSizeString = this.getSystemParam(ssmClient, EV_KEY_MULTIPART_UPLOAD_STANDARD_FILE_SIZE);

    long maxFileSize = Long.valueOf(maxFileSizeString).longValue();//5000000000L; // 5GB

    File file = new File(outputAviFileFullPath);
    long fileSize = file.length();

    return fileSize > maxFileSize;
  }

  /** ファイルアップロード(配信用パケット).(マルチパートアップロード)
   * @param s3Client S3クライアント.
   * @param ssmClient ssmクライアント.
   * @param outputAviFileFullPath 変更済みファイル.
   * @param bucketNameLive バケット名
   * @param s3ObjectKey 格納先のS3キー
   * */
  private String isUsingMultipartUpload(AmazonS3 s3Client, AWSSimpleSystemsManagement ssmClient,
      String outputAviFileFullPath, String bucketNameLive, String s3ObjectKey) {

    int partSize = Integer.valueOf(this.getSystemParam(ssmClient, EV_KEY_MULTIPART_UPLOAD_BLOCK_SIZE)).intValue();

    File file = new File(outputAviFileFullPath);
    long contentLength = file.length();

    InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(bucketNameLive, s3ObjectKey);
    InitiateMultipartUploadResult initResponse = s3Client.initiateMultipartUpload(initRequest);
    String uploadId = initResponse.getUploadId();

    long filePosition = 0;
    int partNumber = 1;
    List<PartETag> partETags = new ArrayList<>();
    while (filePosition < contentLength) {
      long partSizeBytes = Math.min(partSize, (contentLength - filePosition));
      UploadPartRequest uploadRequest = new UploadPartRequest()
          .withBucketName(bucketNameLive)
          .withKey(s3ObjectKey)

          .withUploadId(uploadId)
          .withPartNumber(partNumber)
          .withFileOffset(filePosition)
          .withPartSize(partSizeBytes)
          .withFile(file);
      UploadPartResult uploadResult = s3Client.uploadPart(uploadRequest);
      PartETag partETag = uploadResult.getPartETag();
      partETags.add(partETag);
      partNumber++;
      filePosition += partSizeBytes;
    }

    CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(bucketNameLive, s3ObjectKey,
        uploadId,
        partETags);
    CompleteMultipartUploadResult uploadResult = s3Client.completeMultipartUpload(compRequest);

    return uploadResult.getKey();
  }

  /** サクセスレスポンス編集.
  * @param url URL
  * @return サクセスレスポンス編集
  * */
  public DownloadAviGetOneTimeUrlResponse createSuccessResponse(String url) {
    DownloadAviGetOneTimeUrlResponse response = new DownloadAviGetOneTimeUrlResponse();
    response.setStatusCode(STATUS_CODE_SUCCESS);
    response.setOneTimeUrl(url);
    return response;
  }

  /** 署名付きURLを生成.
   * @param objectKey オブジェクキー
   * */
  public String createOneTimeUrl(String objectKey) {
    AmazonS3 s3Client = this.initS3Cloud();
    AWSSimpleSystemsManagement ssmClient = this.initSsmCloud();
    // 有効期限の設定
    Date sysDatetime = CommonUtils.toDate(CommonUtils.getSystemDateTime());
    long presignedUrlTimeLimitMs = Long.valueOf(this.getSystemParam(ssmClient, EV_KEY_PRESIGNED_URL_TIME_LIMIT_MS));

    Date expiration = new Date(sysDatetime.getTime() + presignedUrlTimeLimitMs);
    long msec = expiration.getTime();
    msec += presignedUrlTimeLimitMs;
    expiration.setTime(msec);
    String bucketNameLive = this.getSystemParam(ssmClient, EV_KEY_BUCKET_NAME_KEY_LIVE);
    // バケットとオブジェクトキーのセット
    GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketNameLive,
        objectKey);

    // リクエストメソッド
    generatePresignedUrlRequest.setMethod(HttpMethod.GET);

    // 有効期限のセット
    generatePresignedUrlRequest.setExpiration(expiration);

    // URL生成
    URL presignedUrl = s3Client.generatePresignedUrl(generatePresignedUrlRequest);

    return presignedUrl.toExternalForm();
  }

  /////////////////////// モード③ end////////////////////////////
  /** エラーレスポンス編集.
  * @param errorCode エラーコード
  * @return エラーレスポンス
  * */
  public DownloadAviGetOneTimeUrlResponse createErrorResponse(DownloadProcessErrorCode errorCode,
      Exception e) {

    DownloadAviGetOneTimeUrlResponse response = new DownloadAviGetOneTimeUrlResponse();
    response.setStatusCode(STATUS_CODE_ERROR);
    response.setErrorCode(errorCode.getErrorCode());

    // システムエラー存在判定
    String systemErrorStr = Objects.isNull(e) ? STRING_EMPTY : CommonUtils.getErrorMsg(e);
    response.setErrorMsg(errorCode.getErrorPatternStrJp() + systemErrorStr);

    this.logger.error(errorCode.getErrorPatternStrJp() + systemErrorStr);
    return response;
  }

  /** パラメータストア情報取得.
  * @param ssmClient ssmクライアント
  * @param paramKey パラメータストアキー名
  * */
  private String getSystemParam(AWSSimpleSystemsManagement ssmClient, String paramKey) {

    String systemparamkey = systemParameterStoreKeyPrefix + paramKey;
    GetParameterRequest getParameterRequest = new GetParameterRequest();
    getParameterRequest.setName(systemparamkey);
    GetParameterResult result = ssmClient.getParameter(getParameterRequest);
    return result.getParameter().getValue();
  }

  /** デバッグログ.
  * @param msgStr メッセージ文字列
  * @param startTime 開始時間
  * */
  private void debugLog(String msgStr, Date startTime) {
    Date end = CommonUtils.toDate(CommonUtils.getSystemDateTime());
    this.logger.debug(msgStr + (end.getTime() - startTime.getTime()));

  }

  /** ディレクトリ削除.
  * @param path ディレクトリパス
  * */
  public void deleteDirectory(String path) {
    try {
      FileUtils.deleteDirectory(new File(path));
    } catch (IOException e) {
      // システムエラー
      String systemErrorStr = Objects.isNull(e) ? STRING_EMPTY : CommonUtils.getErrorMsg(e);
      this.logger.error(systemErrorStr);
    }
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

  /** 2時間前古い作業ディレクトリ削除.
  * @param directoryPath 削除対処作業ディレクトリ
  * */
  public void delete2HoursOldFolders(String directoryPath) {

    File directory = new File(directoryPath);

    // ディレクトリ内のフォルダ一覧を取得
    File[] folders = directory.listFiles(File::isDirectory);

    // システム日時
    LocalDateTime systemDateTime = CommonUtils.getSystemDateTime();

    // システム日時から2時間前の日時を取得
    LocalDateTime systemDateTimeminus1h = systemDateTime.minusHours(2);

    if (Objects.nonNull(folders)) {

      // フォルダを削除
      for (File folder : folders) {

        // 最終更新日時
        LocalDateTime localCreatedDate = CommonUtils.toLocalDateTime(folder.lastModified());

        if (localCreatedDate.isBefore(systemDateTimeminus1h)) {

          this.logger.warn("旧作業フォルダー削除" + folder.getAbsolutePath());
          this.deleteDirectory(folder.getAbsolutePath());
        }
      }
    }
  }

  /** s3クライアント初期化.
  * */
  private AmazonS3 initS3Cloud() {
    AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(Regions.AP_NORTHEAST_1).build();
    return s3Client;
  }

  /** ssmクライアント初期化.*/
  private AWSSimpleSystemsManagement initSsmCloud() {
    AWSSimpleSystemsManagement ssmClient = AWSSimpleSystemsManagementClientBuilder.standard()
        .withRegion(Regions.AP_NORTHEAST_1).build();
    return ssmClient;
  }

}
