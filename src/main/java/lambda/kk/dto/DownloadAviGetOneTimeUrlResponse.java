package lambda.kk.dto;

import java.io.Serializable;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class DownloadAviGetOneTimeUrlResponse implements Serializable {
  /** ワンタイムURL.*/
  private String oneTimeUrl;
  /** ステータスコード.*/
  private String statusCode;
  /** メッセージ.*/
  private String errorMsg;
  /** エラーコード.*/
  private String errorCode;
}
