class RingBuffer {
  final int capacity;
  final List<double> _buf;
  int _start = 0;
  int _size = 0;

  RingBuffer(this.capacity) : _buf = List<double>.filled(capacity, 0.0);

  void add(double v) {
    if (_size < capacity) {
      _buf[(_start + _size) % capacity] = v;
      _size++;
    } else {
      _buf[_start] = v;
      _start = (_start + 1) % capacity;
    }
  }

  List<double> toList() {
    final List<double> result = List<double>.filled(_size, 0.0);
    for (int i = 0; i < _size; i++) {
      result[i] = _buf[(_start + i) % capacity];
    }
    return result;
  }
}
