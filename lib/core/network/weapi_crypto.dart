import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart' as crypto;
import 'package:pointycastle/export.dart';

/// 网易云 weapi 加密（逐字节复刻原版 WeapiCrypto.kt）。
///
/// 协议：
/// ```text
/// secretKey = 16 位随机 [a-zA-Z0-9]
/// params    = B64(AES-CBC-128(B64(AES-CBC-128(payload, K1)), secretKey))
///             K1 = "0CoJUm6Qyw8W8jud"，IV = "0102030405060708"（两次同 IV）
/// encSecKey = RSA-noPadding(reverse(secretKey), e=0x10001, n=官方模数) → hex 左补零 256 位
/// ```
class WeapiCrypto {
  static const _presetKey = '0CoJUm6Qyw8W8jud';
  static const _iv = '0102030405060708';
  static const _pubKey = '010001';

  /// 官方公钥模数（逐字复制原版 RSA_MODULUS 四段拼接，257 hex 含前导 00）
  static const _modulus =
      '00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b7251'
      '52b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93'
      '870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97dde'
      'f52741d546b8e289dc6935b3ece0462db0a22b8e7';

  static const _secretChars =
      'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';

  /// 原版使用 SecureRandom；Dart 对应 Random.secure()
  static final Random _random = Random.secure();

  /// 生成表单体 {params, encSecKey}
  static Map<String, String> encrypt(Map<String, Object?> payload) {
    final json = jsonEncode(payload);
    final secretKey = _randomSecretKey();
    return {
      'params': _encryptParams(json, secretKey),
      'encSecKey': _rsaEncrypt(secretKey),
    };
  }

  static String _randomSecretKey() => String.fromCharCodes(
        List.generate(
          16,
          (_) => _secretChars.codeUnitAt(
            _random.nextInt(_secretChars.length),
          ),
        ),
      );

  /// 双层 AES-CBC（PKCS7=PKCS5 for AES），两次同 IV，中间 Base64
  static String _encryptParams(String payload, String key) =>
      _aesCbc(_aesCbc(payload, _presetKey), key);

  /// AES-128-CBC + PKCS7（等价 Kotlin 的 PKCS5Padding），输出 Base64
  static String _aesCbc(String plain, String key) {
    final cipher = PaddedBlockCipher('AES/CBC/PKCS7')
      ..init(
        true,
        PaddedBlockCipherParameters(
          ParametersWithIV(
            KeyParameter(Uint8List.fromList(utf8.encode(key))),
            Uint8List.fromList(utf8.encode(_iv)),
          ),
          null,
        ),
      );
    return base64Encode(cipher.process(Uint8List.fromList(utf8.encode(plain))));
  }

  /// 教科书 RSA（对齐原版 BigInteger(bytes).modPow(e, n)）：
  /// 字符串反序 → UTF-8 字节大端转 BigInt → modPow → hex 补零 256
  static String _rsaEncrypt(String secretKey) {
    final data = utf8.encode(secretKey.split('').reversed.join(''));
    final base = _bytesToBigInt(data);
    final e = BigInt.parse(_pubKey, radix: 16);
    final n = BigInt.parse(_modulus, radix: 16);
    final result = base.modPow(e, n);
    return result.toRadixString(16).padLeft(256, '0');
  }

  static BigInt _bytesToBigInt(List<int> bytes) {
    var result = BigInt.zero;
    for (final b in bytes) {
      result = (result << 8) | BigInt.from(b & 0xFF);
    }
    return result;
  }

  /// NMTID 种子（MD5 时间戳摘要取段，对齐原版 NeteaseClient 逻辑）
  static String randomNmtid() {
    final millis = DateTime.now().millisecondsSinceEpoch.toString();
    final hex = crypto.md5.convert(utf8.encode(millis)).toString();
    return (int.parse(hex.substring(0, 8), radix: 16) % 0x7FFFFFFF).toString();
  }
}
