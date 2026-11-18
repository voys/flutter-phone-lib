import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Exchanges a VoIPGRID portal login for the API token the middleware needs.
class Voipgrid extends ChangeNotifier {
  Voipgrid(this._storage);

  static final _apiTokenUrl =
      Uri.parse('https://partner.voipgrid.nl/api/permission/apitoken/');
  static final _userDetailsUrl =
      Uri.parse('https://partner.voipgrid.nl/api/v2/user/details');

  final SharedPreferences _storage;

  VoipgridUser? _user;
  VoipgridUser? get user => _user;

  String get username => _storage.getString('voipgrid_username') ?? '';
  String get password => _storage.getString('voipgrid_password') ?? '';
  String get apiToken => _storage.getString('voipgrid_api_token') ?? '';

  bool get hasLogin => username.isNotEmpty && password.isNotEmpty;
  bool get isLoggedIn => apiToken.isNotEmpty;

  Future<void> saveLogin({
    required String username,
    required String password,
  }) async {
    await _storage.setString('voipgrid_username', username);
    await _storage.setString('voipgrid_password', password);
    notifyListeners();
  }

  Future<void> login() async {
    debugPrint('Logging in to VoIPGRID as $username: POST $_apiTokenUrl');

    final response = await http.post(
      _apiTokenUrl,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'email': username, 'password': password}),
    );

    debugPrint('POST $_apiTokenUrl -> ${response.statusCode}');

    if (response.statusCode < 200 || response.statusCode >= 300) {
      await logout();
      throw VoipgridException(
        'VoIPGRID login failed (${response.statusCode}): ${response.body}',
      );
    }

    final token = (jsonDecode(response.body) as Map)['api_token'] as String?;

    if (token == null || token.isEmpty) {
      await logout();
      throw VoipgridException(
        'VoIPGRID login returned no API token: ${response.body}',
      );
    }

    await _storage.setString('voipgrid_api_token', token);
    debugPrint('Logged in to VoIPGRID');
    notifyListeners();
  }

  /// Fetches the logged in user's own details. Failures are only logged since
  /// the details are informational.
  Future<void> fetchUser() async {
    debugPrint('GET $_userDetailsUrl');

    try {
      final response = await http.get(
        _userDetailsUrl,
        headers: {'Authorization': 'Bearer $apiToken'},
      );

      debugPrint('GET $_userDetailsUrl -> ${response.statusCode}');

      if (response.statusCode < 200 || response.statusCode >= 300) return;

      _user = VoipgridUser.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
      notifyListeners();
    } catch (e) {
      debugPrint('Fetching VoIPGRID user failed: $e');
    }
  }

  Future<void> logout() async {
    await _storage.remove('voipgrid_api_token');
    _user = null;
    notifyListeners();
  }
}

class VoipgridUser {
  VoipgridUser.fromJson(Map<String, dynamic> json)
      : name = [json['first_name'], json['preposition'], json['last_name']]
            .whereType<String>()
            .where((part) => part.isNotEmpty)
            .join(' '),
        internalNumber = json['internal_number']?.toString(),
        voipAccountNumber =
            json['app']?['voip_account']?['internal_number']?.toString(),
        isDestinationElsewhere = _isDestinationElsewhere(
          json['app']?['voip_account'],
          json['selected_destination']?['voip_account'],
        );

  final String name;
  final String? internalNumber;
  final String? voipAccountNumber;

  /// Incoming calls go to another destination than this app's VoIP account.
  final bool isDestinationElsewhere;

  static bool _isDestinationElsewhere(dynamic account, dynamic selected) =>
      account is Map &&
      (selected is! Map || '${selected['id']}' != '${account['id']}');
}

class VoipgridException implements Exception {
  const VoipgridException(this.message);

  final String message;

  @override
  String toString() => message;
}
