import 'package:flutter/material.dart';
import 'package:flutter_phone_lib/flutter_phone_lib.dart';

class CallerHeader extends StatelessWidget {
  const CallerHeader({super.key, required this.call, required this.status});

  final Call? call;
  final Widget status;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final call = this.call;
    final initials = call == null ? '' : _initials(call.remotePartyHeading);

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        CircleAvatar(
          radius: 48,
          backgroundColor: theme.colorScheme.primaryContainer,
          foregroundColor: theme.colorScheme.onPrimaryContainer,
          child: initials.isEmpty
              ? const Icon(Icons.person, size: 48)
              : Text(initials, style: theme.textTheme.headlineMedium),
        ),
        const SizedBox(height: 24),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Text(
            call?.remotePartyHeading ?? '',
            style: theme.textTheme.headlineMedium,
            textAlign: TextAlign.center,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        if (call?.remotePartySubheading.isNotEmpty ?? false) ...[
          const SizedBox(height: 4),
          Text(
            call!.remotePartySubheading,
            style: theme.textTheme.bodyLarge?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
        const SizedBox(height: 12),
        DefaultTextStyle.merge(
          style: theme.textTheme.titleMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
          child: status,
        ),
      ],
    );
  }

  /// Phone numbers have no sensible initials, so only names produce any.
  static String _initials(String name) {
    final words = name
        .split(RegExp(r'\s+'))
        .where((word) =>
            word.isNotEmpty && RegExp(r'^\p{L}', unicode: true).hasMatch(word))
        .toList();

    if (words.isEmpty) return '';

    final first = words.first.characters.first;
    final last = words.length > 1 ? words.last.characters.first : '';

    return (first + last).toUpperCase();
  }
}
