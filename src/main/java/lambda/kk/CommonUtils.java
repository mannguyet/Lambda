package lambda.kk;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.net.URLCodec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CommonUtils {
  /** JST_TIME_ZONE_ID. */
  private static final String ZONE_ID_STRING_JST = "Asia/Tokyo";
  /** ランダム文字列範囲. */
  private static final String CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  /**
   * 文字列ブランク判定.
   * @param cs 入力
   * @return true:ブランク , false:有効文字列
   * */
  public static boolean stringIsBlank(final CharSequence cs) {
    final int strLen = length(cs);
    if (strLen == 0) {
      return true;
    }
    for (int i = 0; i < strLen; i++) {
      if (!Character.isWhitespace(cs.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /** 文字数取得.
   *  @param cs 入力
   *  @return 文字数
   * */
  public static int length(final CharSequence cs) {
    return cs == null ? 0 : cs.length();
  }

  /** マップto文字列変換.
   *  @param inputMap 入力マップ
   *  @return 文字列
   * */
  public static String coverMapToString(Map<String, Object> inputMap) {
    try {
      String json = new ObjectMapper().writeValueAsString(inputMap);

      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonob = mapper.readTree(json);

      return jsonob.toPrettyString();

    } catch (Exception e) {
      return e.getMessage();
    }

  }

  /** システム日時取得(JST).
   *  @return システム日時
   * */
  public static LocalDateTime getSystemDateTime() {
    ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of(ZONE_ID_STRING_JST));
    return zonedDateTime.toLocalDateTime();
  }

  /** 日時文字列をDate型に変換.
   *  @return システム日時
   * */
  public static Date coverStringToDate(String strDate) throws ParseException {

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    return sdf.parse(strDate);
  }

  /** LocalDateTimeをDate型に変換.
   *  @return システム日時
   * */
  public static Date toDate(LocalDateTime localDateTime) {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.of(ZONE_ID_STRING_JST));
    Instant instant = zonedDateTime.toInstant();
    return Date.from(instant);
  }

  /** longをLocalDateTime型に変換.
   *  @return システム日時
   * */
  public static LocalDateTime toLocalDateTime(long longtimes) {
    Date date = new Date(longtimes);
    return date.toInstant().atZone(ZoneId.of(ZONE_ID_STRING_JST)).toLocalDateTime();
  }

  /** AES復号処理.
   * @param str 暗号文
   * @param encKey 暗号化共通鍵.
   * @param iVec 暗号化ベクター.
   * @param charset 文字コード
   * @return 平文
   */
  public static String decode(String str, String encKey, String iVec, String charset) throws Exception {

    URLCodec codec = new URLCodec(charset);
    str = codec.decode(str, charset);

    SecretKeySpec key = new SecretKeySpec(encKey.getBytes(charset), "AES");
    IvParameterSpec iv = new IvParameterSpec(iVec.getBytes(charset));

    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.DECRYPT_MODE, key, iv);
    byte[] byteResult = cipher.doFinal(Base64.getDecoder().decode(str));

    return new String(byteResult, charset);
  }

  /** IntegerをString型に変換.
   *  @return  String型value(null→"")
   * */
  public static String coverIntegerToString(Integer param) {
    if (param == null) {
      return "";
    } else {
      return String.valueOf(param);
    }
  }

  /** Integerをint型に変換.
   *  @return int型value(null→0)
   * */
  public static int coverIntegerToInt(Integer param) {
    if (param == null) {
      return 0;
    } else {
      return param.intValue();
    }
  }

  /** エラーメッセージ取得.
   * @param e 例外
   *  @return エラーメッセージ
   * */
  public static String getErrorMsg(Exception e) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    return sw.toString();
  }

  /** ファイルリネーム*/
  public static void rename(String before, String after) {
    // 前ファイル削除 .avi
    new File(before).delete();
    // ファイルのリネーム xxxcuted_tmp.avi→xxx.avi
    new File(after).renameTo(new File(before));
  }

  /** ハッシュ値算出*/
  public static String hash(String inputStr) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      byte[] inputBytes = inputStr.getBytes();
      byte[] hashBytes = messageDigest.digest(inputBytes);
      StringBuilder builder = new StringBuilder();
      for (byte b : hashBytes) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  /** ランダム文字列生成*/
  public static String generate(int length) {

    StringBuilder builder = new StringBuilder(length);

    Random random = new SecureRandom();

    for (int i = 0; i < length; i++) {
      builder.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
    }
    return builder.toString();
  }

  public static String formatMapToTreeJsonString(Map<?, ?> map) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      sb.append("  ")
          .append(entry.getKey())
          .append(": ")
          .append(entry.getValue())
          .append(",\n");
    }
    if (!map.isEmpty()) {
      sb.setLength(sb.length() - 2);
    }
    sb.append("\n}");
    return sb.toString();
  }
}
