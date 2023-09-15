package lambda.kk.codes;

public enum DownloadProcessErrorCode {
  /** LBDE2000:異常終了:リクエスト変換エラー. */
  INPUT_COVER_ERROR("LBDE2000", "異常終了:リクエスト変換エラー."),
  /** LBDE2001:異常終了:入力チェック. */
  INPUT_CHECK_ERROR("LBDE2001", "異常終了:入力チェック."),
  /** LBDE2002:異常終了:オブジェクトキー編集. */
  OBJECT_KEY_EDIT_ERROR("LBDE2002", "異常終了:オブジェクトキー編集."),
  /** LBDE2003:異常終了:作業フォルダ初期化. */
  INIT_WORK_FOLDER_ERROR("LBDE2003", "異常終了:作業フォルダ初期化."),
  /** LBDE2004:異常終了:中間テキストファイル作成. */
  CREATE_INTERMEDIATE_TEXT_FILE_ERROR("LBDE2004", "異常終了:中間テキストファイル作成."),
  /** LBDE2005:異常終了:標準画像基準情報集計."*/
  AGGREGATE_STANDARD_IMAGE_INFORMATION_ERROR("LBDE2005", "異常終了:標準画像基準情報集計."),
  /** LBDE2006:異常終了:S3ファイル取得. */
  GET_S3_FILE_ERROR("LBDE2006", "異常終了:S3ファイル取得."),
  /** LBDE2007:異常終了:中間画像編集. */
  EDIT_INTERMEDIATE_IMAGE_ERROR("LBDE2007", "異常終了:中間画像編集."),
  /** LBDE2008:異常終了:画像結合. */
  COMBINE_IMAGES_ERROR("LBDE2008", "異常終了:画像結合."),
  /** LBDE2009:異常終了:S3アップロード. */
  UPLOAD_TO_S3_ERROR("LBDE2009", "異常終了:S3アップロード."),
  /** LBDE2010:異常終了:作業フォルダクリーン. */
  CLEAN_WORK_FOLDER_ERROR("LBDE2010", "異常終了:作業フォルダクリーン."),
  /** LBDE2011:異常終了:ワンタイムURL発行. */
  GENERATE_ONE_TIME_URL_ERROR("LBDE2011", "異常終了:ワンタイムURL発行.");

  /** エラーコード. */
  private final String errorCode;
  /** 日本語メッセージ. */
  private final String errorPatternStrJp;

  private DownloadProcessErrorCode(String errorCode, String errorPatternStrJp) { // コンストラクタの定義
    this.errorCode = errorCode;
    this.errorPatternStrJp = errorPatternStrJp;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorPatternStrJp() {
    return errorPatternStrJp;
  }

}
