package art.arcane.react.content.feature;

final class FastExplosionWindow {
  private int primes;
  private int preprimes;
  private int explosionChains;

  synchronized int nextPrimeIndex() {
    return primes++;
  }

  synchronized int reservePrimes(int requested, int maximum) {
    int safeRequested = Math.max(0, requested);
    int available = Math.max(0, maximum) - preprimes;
    int reserved = Math.min(safeRequested, Math.max(0, available));
    preprimes += reserved;
    return reserved;
  }

  synchronized boolean tryAcquireExplosionChain(int maximum) {
    boolean acquired = explosionChains < Math.max(0, maximum);
    explosionChains++;
    return acquired;
  }

  synchronized void reset() {
    primes = 0;
    preprimes = 0;
    explosionChains = 0;
  }
}
