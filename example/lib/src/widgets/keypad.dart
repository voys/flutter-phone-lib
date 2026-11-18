import 'package:flutter/material.dart';

const _keys = [
  [('1', ''), ('2', 'ABC'), ('3', 'DEF')],
  [('4', 'GHI'), ('5', 'JKL'), ('6', 'MNO')],
  [('7', 'PQRS'), ('8', 'TUV'), ('9', 'WXYZ')],
  [('*', ''), ('0', '+'), ('#', '')],
];

class Keypad extends StatelessWidget {
  const Keypad({super.key, required this.onKeyPressed, this.onPlus});

  static const keySize = 72.0;
  static const keyPadding = EdgeInsets.symmetric(horizontal: 12, vertical: 6);

  final void Function(String key) onKeyPressed;

  /// Called on a long press of 0. When null, 0 shows no "+" hint, which suits
  /// DTMF where "+" can't be sent.
  final VoidCallback? onPlus;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (final row in _keys)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final (key, letters) in row)
                Padding(
                  padding: keyPadding,
                  child: _Key(
                    digit: key,
                    letters: key == '0' && onPlus == null ? '' : letters,
                    onPressed: () => onKeyPressed(key),
                    onLongPress: key == '0' ? onPlus : null,
                  ),
                ),
            ],
          ),
      ],
    );
  }
}

class _Key extends StatelessWidget {
  const _Key({
    required this.digit,
    required this.letters,
    required this.onPressed,
    this.onLongPress,
  });

  final String digit;
  final String letters;
  final VoidCallback onPressed;
  final VoidCallback? onLongPress;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Material(
      color: theme.colorScheme.surfaceContainerHigh,
      shape: const CircleBorder(),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onPressed,
        onLongPress: onLongPress,
        child: SizedBox.square(
          dimension: Keypad.keySize,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                digit,
                style: theme.textTheme.headlineMedium?.copyWith(height: 1.1),
              ),
              Text(
                letters,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  letterSpacing: 1.5,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
