package lambda.kk.dto;

import java.io.Serializable;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CoverHlsFileToS3Request implements Serializable {

  /** 設置機器論理番号. */
  private String lnDev;

  /** 開始時間. */
  private String shotSttTstm;

  /** 映像時間. */
  private Integer duration;

  /** 蓄積パケット名. */
  private String bucket;

  /** ファイルパス. */
  private String path;

  /** 解像度. */
  private String resolution;

  /** フレームレート. */
  private String frameRate;

  /** カットフラグ (0不要.1 後方,2 前方カット).  */
  private Integer videoCutKindCode;

  /** 黒画像フラグ. */
  private Integer blackFlg;

  /** 黒画像ファイル名. */
  private String blackFileName;

  /** 制限時間. */
  private String activeLimit;

}
