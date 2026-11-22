import type { KeyValueStorage, ReminderPreferences } from '../types/reminders';

const PREFERENCES_KEY_PREFIX = 'dosecerta:reminders:preferences:';

export function createDefaultReminderPreferences(now: Date = new Date()): ReminderPreferences {
  return {
    enabled: false,
    hideSensitiveDetailsOnLockScreen: true,
    updatedAt: now.toISOString(),
  };
}

/**
 * Gerencia a persistência local das preferências de lembretes isoladas por usuário (userId).
 */
export class ReminderPreferencesStore {
  private readonly storage: KeyValueStorage;
  private readonly nowProvider: () => Date;

  constructor(storage: KeyValueStorage, nowProvider: () => Date = () => new Date()) {
    this.storage = storage;
    this.nowProvider = nowProvider;
  }

  private keyFor(userId: string): string {
    return `${PREFERENCES_KEY_PREFIX}${userId}`;
  }

  async getPreferences(userId: string): Promise<ReminderPreferences> {
    const raw = await this.storage.getItem(this.keyFor(userId));
    if (!raw) {
      return createDefaultReminderPreferences(this.nowProvider());
    }
    try {
      const parsed = JSON.parse(raw) as Partial<ReminderPreferences>;
      return {
        enabled: Boolean(parsed.enabled),
        hideSensitiveDetailsOnLockScreen:
          typeof parsed.hideSensitiveDetailsOnLockScreen === 'boolean'
            ? parsed.hideSensitiveDetailsOnLockScreen
            : true,
        updatedAt: typeof parsed.updatedAt === 'string' ? parsed.updatedAt : this.nowProvider().toISOString(),
      };
    } catch {
      return createDefaultReminderPreferences(this.nowProvider());
    }
  }

  async savePreferences(
    userId: string,
    patch: Partial<Pick<ReminderPreferences, 'enabled' | 'hideSensitiveDetailsOnLockScreen'>>,
  ): Promise<ReminderPreferences> {
    const current = await this.getPreferences(userId);
    const updated: ReminderPreferences = {
      enabled: patch.enabled !== undefined ? patch.enabled : current.enabled,
      hideSensitiveDetailsOnLockScreen:
        patch.hideSensitiveDetailsOnLockScreen !== undefined
          ? patch.hideSensitiveDetailsOnLockScreen
          : current.hideSensitiveDetailsOnLockScreen,
      updatedAt: this.nowProvider().toISOString(),
    };
    await this.storage.setItem(this.keyFor(userId), JSON.stringify(updated));
    return updated;
  }

  async setEnabled(userId: string, enabled: boolean): Promise<ReminderPreferences> {
    return this.savePreferences(userId, { enabled });
  }
}

