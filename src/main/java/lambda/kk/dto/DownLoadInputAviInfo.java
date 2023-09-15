package lambda.kk.dto;

import java.io.Serializable;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class DownLoadInputAviInfo implements Serializable {
  /** S3パケット名. */
  private String bucket;
  /** オブジェクトキー. */
  private String objectkey;
  /** 解像度. */
  private String resolution;
  /** フレームレート. */
  private String frameRate;
  /** カットフラグ (0不要.1 後方,2 前方カット).  */
  private Integer videoCutKindCode;
  /** 映像時間. */
  private Integer duration;
}