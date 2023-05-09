import 'package:flutter/material.dart';
import 'package:get/get.dart';

void main() {
  _init().then((value) => runApp(const ReactApp()));
}

Future<void> _init() async {}

class ReactApp extends StatefulWidget {
  const ReactApp({Key? key}) : super(key: key);

  @override
  State<ReactApp> createState() => _ReactAppState();
}

class _ReactAppState extends State<ReactApp> {
  @override
  Widget build(BuildContext context) => const GetMaterialApp(
        color: Colors.lightBlue,
        title: "React Remote",
      );
}
