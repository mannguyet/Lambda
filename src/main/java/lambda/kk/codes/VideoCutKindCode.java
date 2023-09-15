package lambda.kk.codes;

public enum VideoCutKindCode {
    NO_CUT(0), FRONT_CUT(1), BACK_CUT(2);

    private int code; // フィールドの定義

    private VideoCutKindCode(int code) { // コンストラクタの定義
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

}
