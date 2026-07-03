// @dart=3.6
// ignore_for_file: type=lint
// build_runner >=2.4.16
import 'dart:io' as _io;
import 'package:build_runner/src/build_plan/builder_factories.dart'
    as _build_runner;
import 'package:build_runner/src/bootstrap/processes.dart' as _build_runner;
import 'package:build_web_compilers/builders.dart' as _i1;
import 'package:jaspr_builder/builder.dart' as _i2;
import 'package:source_gen/builder.dart' as _i3;

final _builderFactories = _build_runner.BuilderFactories(
  {
    'build_web_compilers:dart2js_modules': [
      _i1.dart2jsMetaModuleBuilder,
      _i1.dart2jsMetaModuleCleanBuilder,
      _i1.dart2jsModuleBuilder
    ],
    'build_web_compilers:dart2wasm_modules': [
      _i1.dart2wasmMetaModuleBuilder,
      _i1.dart2wasmMetaModuleCleanBuilder,
      _i1.dart2wasmModuleBuilder
    ],
    'build_web_compilers:ddc': [_i1.ddcKernelBuilder, _i1.ddcBuilder],
    'build_web_compilers:ddc_modules': [
      _i1.ddcMetaModuleBuilder,
      _i1.ddcMetaModuleCleanBuilder,
      _i1.ddcModuleBuilder
    ],
    'build_web_compilers:entrypoint': [_i1.webEntrypointBuilder],
    'build_web_compilers:entrypoint_marker': [_i1.webEntrypointMarkerBuilder],
    'build_web_compilers:module_library': [_i1.moduleLibraryBuilder],
    'build_web_compilers:sdk_js': [_i1.sdkJsCompile, _i1.sdkJsCopyRequirejs],
    'jaspr_builder:client_entrypoint': [_i2.buildClientEntrypoint],
    'jaspr_builder:client_module': [_i2.buildClientModule],
    'jaspr_builder:client_options': [_i2.buildClientOptions],
    'jaspr_builder:clients_bundle': [_i2.buildClientsBundle],
    'jaspr_builder:codec_bundle': [_i2.buildCodecBundle],
    'jaspr_builder:codec_module': [_i2.buildCodecModule],
    'jaspr_builder:import_output': [_i2.buildImportsOutput],
    'jaspr_builder:imports_module': [_i2.buildImportsModule],
    'jaspr_builder:server_options': [_i2.buildServerOptions],
    'jaspr_builder:stub': [_i2.buildPlatformStubs],
    'jaspr_builder:styles_bundle': [_i2.buildStylesBundle],
    'jaspr_builder:styles_module': [_i2.buildStylesModule],
    'jaspr_builder:styles_standalone': [_i2.buildStylesStandalone],
    'jaspr_builder:sync_mixins_module': [_i2.buildSyncMixins],
    'source_gen:combining_builder': [_i3.combiningBuilder],
  },
  postProcessBuilderFactories: {
    'build_web_compilers:dart2js_archive_extractor':
        _i1.dart2jsArchiveExtractor,
    'build_web_compilers:dart_source_cleanup': _i1.dartSourceCleanup,
    'build_web_compilers:module_cleanup': _i1.moduleCleanup,
    'source_gen:part_cleanup': _i3.partCleanup,
  },
);
void main(List<String> args) async {
  _io.exitCode = await _build_runner.ChildProcess.run(
    args,
    _builderFactories,
  )!;
}
