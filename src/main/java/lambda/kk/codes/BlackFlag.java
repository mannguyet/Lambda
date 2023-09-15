package lambda.kk.codes;

public enum BlackFlag {
  NORMAL(0), GET_BLACK_FILE(1);

  private int code; // フィールドの定義

  private BlackFlag(int code) { // コンストラクタの定義
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public void setCode(int code) {
    this.code = code;
  }

}
