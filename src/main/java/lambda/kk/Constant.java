package lambda.kk;

public interface Constant {
  //記号
  /** 半角カンマ. */
  public static final String STRING_COMMA_COLON = ",";
  /** 半角スラッシュ. */
  public static final String STRING_SYMBOL_SLASH = "/";
  /** 空文字. */
  public static final String STRING_EMPTY = "";
  /** 半角スペース. */
  public static final String STRING_HALF_SPACE = " ";
  /** 半角アンダーバー. */
  public static final String STRING_HALF_UNDERBAR = "_";
  /** 半角ハイフォン. */
  public static final String STRING_HALF_HYPHEN = "-";

  // 定数
  /** 正規表現:ファイル名抽出用. */
  public static final String DIR_REGEXP = "^.*/";
  /** 文字列 通常パターンavi拡張子. */
  public static final String EXTENSION_AVI = ".avi";
  /** 文字列 一時ファイルtmpavi拡張子. */
  public static final String FU_EXTENSION_AVI = "tmp.avi";
  /** 文字列 カットあり拡張子. */
  public static final String CUTED_EXTENSION_AVI = "cuted_tmp.avi";
  /** コマンド用テキストファイル名. */
  public static final String CMD_INPUT_TEXT_FILE_NAME = "inputAviList.txt";
  /** ランダム文字列長さ. */
  public static final int RANDOM_STRING_LENGTH = 10;
  /** 異常終了. */
  public static final String STATUS_CODE_ERROR = "500";
  /** 正常終了. */
  public static final String STATUS_CODE_SUCCESS = "200";
}
