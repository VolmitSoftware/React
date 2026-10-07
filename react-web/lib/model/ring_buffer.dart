import 'dart:collection';

class RingBuffer {
  static const int _blockSize = 16;
  final int capacity;
  final List<List<double>> _blocks;
  final ListQueue<double> _minima = ListQueue<double>();
  final ListQueue<double> _maxima = ListQueue<double>();
  int _start = 0;
  int _size = 0;
  bool _shared = false;
  List<double>? _snapshot;

  RingBuffer(this.capacity)
    : _blocks = List<List<double>>.generate(
        (capacity + _blockSize - 1) ~/ _blockSize,
        (int index) => List<double>.filled(_blockSize, 0),
      ) {
    if (capacity <= 0) throw ArgumentError.value(capacity, 'capacity');
  }

  double? get minimum => _minima.isEmpty ? null : _minima.first;
  double? get maximum => _maxima.isEmpty ? null : _maxima.first;

  void add(double value) {
    if (!value.isFinite) return;
    final int slot = (_start + _size) % capacity;
    if (_size == capacity) {
      final double removed = _blocks[_start ~/ _blockSize][_start % _blockSize];
      if (_minima.first == removed) _minima.removeFirst();
      if (_maxima.first == removed) _maxima.removeFirst();
      _start = (_start + 1) % capacity;
    } else {
      _size++;
    }
    final int block = slot ~/ _blockSize;
    if (_shared) {
      _blocks[block] = List<double>.of(_blocks[block]);
    }
    _blocks[block][slot % _blockSize] = value;
    while (_minima.isNotEmpty && _minima.last > value) {
      _minima.removeLast();
    }
    while (_maxima.isNotEmpty && _maxima.last < value) {
      _maxima.removeLast();
    }
    _minima.addLast(value);
    _maxima.addLast(value);
    _snapshot = null;
  }

  List<double> snapshot() {
    _shared = true;
    return _snapshot ??= _RingHistory(
      List<List<double>>.of(_blocks),
      _start,
      _size,
      capacity,
    );
  }

  List<double> toList() => List<double>.of(snapshot());
}

final class _RingHistory extends ListBase<double> {
  final List<List<double>> _blocks;
  final int _start;
  final int _size;
  final int _capacity;

  _RingHistory(this._blocks, this._start, this._size, this._capacity);

  @override
  int get length => _size;

  @override
  set length(int value) => throw UnsupportedError('Immutable history');

  @override
  double operator [](int index) {
    RangeError.checkValidIndex(index, this, 'index', _size);
    final int slot = (_start + index) % _capacity;
    return _blocks[slot ~/ RingBuffer._blockSize][slot % RingBuffer._blockSize];
  }

  @override
  void operator []=(int index, double value) =>
      throw UnsupportedError('Immutable history');
}
