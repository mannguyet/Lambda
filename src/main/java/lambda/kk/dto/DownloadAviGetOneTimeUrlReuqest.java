package lambda.kk.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class DownloadAviGetOneTimeUrlReuqest implements Serializable {
  /** ファイルリスト.*/
  private List<DownLoadInputAviInfo> fileList;
  /** ファイルリストサイズ.*/
  private int fileCount;
  /** 初期処理_処理結果.*/
  private InitProcessOutPut initProcessResultJson;
  /** 中間処理_処理結果.*/
  private List<DownloadAviGetOneTimeUrlResponse> editProcessesResultJson;

}