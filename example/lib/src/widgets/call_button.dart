import 'package:flutter/material.dart';

/// The fixed answer and hang-up colors that phone apps use regardless of the
/// theme, so they stay recognizable in both light and dark mode.
const answerColor = Color(0xFF1E8E3E);
const hangUpColor = Color(0xFFD93025);

class CallButton extends StatelessWidget {
  const CallButton({
    super.key,
    required this.icon,
    required this.color,
    required this.onPressed,
    required this.tooltip,
    this.width = 72,
    this.height = 72,
  });

  const CallButton.answer({
    super.key,
    required this.onPressed,
    this.tooltip = 'Call',
    this.width = 72,
    this.height = 72,
  })  : icon = Icons.call,
        color = answerColor;

  const CallButton.hangUp({
    super.key,
    required this.onPressed,
    this.tooltip = 'End call',
    this.width = 72,
    this.height = 72,
  })  : icon = Icons.call_end,
        color = hangUpColor;

  final IconData icon;
  final Color color;
  final VoidCallback? onPressed;
  final String tooltip;
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: SizedBox(
        width: width,
        height: height,
        child: FilledButton(
          onPressed: onPressed,
          style: FilledButton.styleFrom(
            backgroundColor: color,
            foregroundColor: Colors.white,
            padding: EdgeInsets.zero,
            shape: const StadiumBorder(),
          ),
          child: Icon(icon, size: 32),
        ),
      ),
    );
  }
}
