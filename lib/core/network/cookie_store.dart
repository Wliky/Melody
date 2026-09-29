import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Cookie 管理（对齐原版 CookieStore.kt）：
/// - 内存 Map + flutter_secure_storage 持久化（原版 DataStore 明文，按文档 §22 迁入安全存储）
/// - 种子 cookie 启动即种：os=pc / appver=8.9.70 / osver / NMTID（防风控 524）
/// - MUSIC_U 登录态、MUSIC_A 游客态、__csrf 写接口防 524
class CookieStore {
  static const _storageKey = 'cookies_merged';
  static const _extraHeaders = <String, String>{
    'os': 'pc',
    'appver': '8.9.70',
    'osver': 'Microsoft-Windows-10',
  };

  final FlutterSecureStorage _storage = const FlutterSecureStorage();
  final Map<String, String> _cookies = {};
  bool _restored = false;

  Map<String, String> get all => Map.unmodifiable(_cookies);

  String? get csrf {
    final v = _cookies['__csrf'];
    return (v == null || v.isEmpty) ? null : v;
  }

  bool get hasLogin => (_cookies['MUSIC_U'] ?? '').isNotEmpty;
  bool get hasGuest => (_cookies['MUSIC_A'] ?? '').isNotEmpty;

  /// 启动恢复 + 种子（与原版一致：接受与首屏请求的轻微竞态）
  Future<void> ensureRestored(String nmtid) async {
    if (_restored) return;
    _restored = true;
    await _restore();
    _applySeeds(nmtid);
  }

  Future<void> _restore() async {
    try {
      final raw = await _storage.read(key: _storageKey);
      if (raw == null || raw.isEmpty) return;
      for (final pair in raw.split('; ')) {
        final i = pair.indexOf('=');
        if (i <= 0) continue;
        _cookies[pair.substring(0, i)] = pair.substring(i + 1);
      }
    } catch (_) {
      // 恢复失败静默：视为全新会话
    }
  }

  void _applySeeds(String nmtid) {
    _cookies.putIfAbsent('NMTID', () => nmtid);
    _extraHeaders.forEach((k, v) => _cookies.putIfAbsent(k, () => v));
  }

  /// 捕获响应 Set-Cookie（单个）
  void captureSetCookie(String setCookie) {
    final first = setCookie.split(';').first.trim();
    final i = first.indexOf('=');
    if (i <= 0) return;
    final name = first.substring(0, i);
    final value = first.substring(i + 1);
    if (value.isEmpty) {
      _cookies.remove(name);
    } else {
      _cookies[name] = value;
    }
    _persist();
  }

  /// 请求头 Cookie 值（k=v; k=v 合并串）
  String toHeader() {
    return _cookies.entries.map((e) => '${e.key}=${e.value}').join('; ');
  }

  Future<void> clear() async {
    _cookies.clear();
    try {
      await _storage.delete(key: _storageKey);
    } catch (_) {}
  }

  Future<void> _persist() async {
    try {
      await _storage.write(key: _storageKey, value: toHeader());
    } catch (_) {
      // 落盘失败静默（对齐原版 runCatching 吞掉）
    }
  }

  /// 调试/测试用：直接注入
  void debugInject(String name, String value) => _cookies[name] = value;

  /// 生成种子 NMTID 的 JSON 序列化占位（未使用，保持私有性）
  static String encodeSeeds(Map<String, String> seeds) =>
      jsonEncode(seeds);
}
