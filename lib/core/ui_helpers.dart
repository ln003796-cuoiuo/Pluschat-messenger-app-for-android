import 'package:flutter/material.dart';

const plusBlue = Color(0xFF2787F5);
const plusInk = Color(0xFF182536);
const plusMuted = Color(0xFF7C8998);
const plusSurface = Color(0xFFF4F7FB);

void showPluschatNotice(BuildContext context, String text) {
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(text), behavior: SnackBarBehavior.floating),
  );
}
