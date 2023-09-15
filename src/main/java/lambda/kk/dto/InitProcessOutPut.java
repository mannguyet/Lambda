package lambda.kk.dto;

import java.io.Serializable;

import lombok.Data;
import lombok.ToString;

/** 初期処理_処理結果.*/
@Data
@ToString
public class InitProcessOutPut implements Serializable {

  /** コンストラクタ. */
  public InitProcessOutPut(String workDir, String inputTextPath, DownloadAviGetOneTimeUrlResponse processResponse) {

    this.workDir = workDir;
    this.inputTextPath = inputTextPath;
    this.processResponse = processResponse;
  }

  /** コンストラクタ. */
  public InitProcessOutPut(DownloadAviGetOneTimeUrlResponse processResponse) {
    this.processResponse = processResponse;
  }

  /** コンストラクタ. */
  public InitProcessOutPut() {

  }

  /** 作業ディレクトリ. */
  private String workDir;

  /** 中間テキストファイルフルパス. */
  private String inputTextPath;

  /** 処理結果. */
  private DownloadAviGetOneTimeUrlResponse processResponse;

}
