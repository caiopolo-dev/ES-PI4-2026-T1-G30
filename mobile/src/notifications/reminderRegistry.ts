import type { KeyValueStorage, ScheduledReminderRecord } from '../types/reminders';

const REGISTRY_KEY_PREFIX = 'dosecerta:reminders:registry:';

type RegistryMap = Record<string, ScheduledReminderRecord>;

/**
 * Mantém a associação local persistida entre a identidade estável da DosePrevista (plannedDoseId)
 * e o identificador de notificação retornado pela plataforma (Android/iOS).
 *
 * Nunca depende de texto ou horário visível como chave primária.
 */
export class ReminderRegistry {
  private readonly storage: KeyValueStorage;

  constructor(storage: KeyValueStorage) {
    this.storage = storage;
  }

  private keyFor(userId: string): string {
    return `${REGISTRY_KEY_PREFIX}${userId}`;
  }

  private async readMap(userId: string): Promise<RegistryMap> {
    const raw = await this.storage.getItem(this.keyFor(userId));
    if (!raw) {
      return {};
    }
    try {
      const parsed = JSON.parse(raw) as RegistryMap;
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch {
      return {};
    }
  }

  private async writeMap(userId: string, map: RegistryMap): Promise<void> {
    await this.storage.setItem(this.keyFor(userId), JSON.stringify(map));
  }

  async getAll(userId: string): Promise<ScheduledReminderRecord[]> {
    const map = await this.readMap(userId);
    return Object.values(map);
  }

  async getByPlannedDoseId(userId: string, plannedDoseId: string): Promise<ScheduledReminderRecord | null> {
    const map = await this.readMap(userId);
    return map[plannedDoseId] ?? null;
  }

  async findByTreatmentId(userId: string, treatmentId: string): Promise<ScheduledReminderRecord[]> {
    const all = await this.getAll(userId);
    return all.filter((item) => item.treatmentId === treatmentId);
  }

  async upsert(userId: string, record: ScheduledReminderRecord): Promise<void> {
    const map = await this.readMap(userId);
    map[record.plannedDoseId] = record;
    await this.writeMap(userId, map);
  }

  async remove(userId: string, plannedDoseId: string): Promise<ScheduledReminderRecord | null> {
    const map = await this.readMap(userId);
    const existing = map[plannedDoseId] ?? null;
    if (existing) {
      delete map[plannedDoseId];
      await this.writeMap(userId, map);
    }
    return existing;
  }

  async removeMany(userId: string, plannedDoseIds: string[]): Promise<void> {
    if (plannedDoseIds.length === 0) {
      return;
    }
    const map = await this.readMap(userId);
    let changed = false;
    for (const id of plannedDoseIds) {
      if (id in map) {
        delete map[id];
        changed = true;
      }
    }
    if (changed) {
      await this.writeMap(userId, map);
    }
  }

  async clear(userId: string): Promise<void> {
    await this.storage.removeItem(this.keyFor(userId));
  }
}

