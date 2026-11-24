import type { KeyValueStorage } from '../types/reminders';

/**
 * Implementação em memória utilizada em testes automatizados e como fallback seguro
 * caso AsyncStorage não esteja disponível no ambiente de execução.
 */
export class InMemoryKeyValueStorage implements KeyValueStorage {
  private readonly store = new Map<string, string>();

  async getItem(key: string): Promise<string | null> {
    return this.store.has(key) ? (this.store.get(key) ?? null) : null;
  }

  async setItem(key: string, value: string): Promise<void> {
    this.store.set(key, value);
  }

  async removeItem(key: string): Promise<void> {
    this.store.delete(key);
  }

  snapshot(): Record<string, string> {
    return Object.fromEntries(this.store.entries());
  }
}

interface AsyncStorageLike {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}

/**
 * Adaptador para @react-native-async-storage/async-storage no runtime React Native.
 */
export class ReactNativeAsyncStorageAdapter implements KeyValueStorage {
  private readonly fallback = new InMemoryKeyValueStorage();
  private asyncStoragePromise: Promise<AsyncStorageLike | null> | null = null;

  private async resolveAsyncStorage(): Promise<AsyncStorageLike | null> {
    if (!this.asyncStoragePromise) {
      this.asyncStoragePromise = import('@react-native-async-storage/async-storage')
        .then((mod) => (mod.default ?? mod) as unknown as AsyncStorageLike)
        .catch(() => null);
    }
    return this.asyncStoragePromise;
  }

  async getItem(key: string): Promise<string | null> {
    const storage = await this.resolveAsyncStorage();
    return storage ? storage.getItem(key) : this.fallback.getItem(key);
  }

  async setItem(key: string, value: string): Promise<void> {
    const storage = await this.resolveAsyncStorage();
    if (storage) {
      await storage.setItem(key, value);
    } else {
      await this.fallback.setItem(key, value);
    }
  }

  async removeItem(key: string): Promise<void> {
    const storage = await this.resolveAsyncStorage();
    if (storage) {
      await storage.removeItem(key);
    } else {
      await this.fallback.removeItem(key);
    }
  }
}

