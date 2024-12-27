import {Injectable, signal, WritableSignal} from '@angular/core';
import {EntryLoadResultType, ExplorerService, StorageEntryDto} from '../../api/se';

@Injectable({
  providedIn: 'root',
})
export class StorageIndexService {

  entries: WritableSignal<Map<string, StorageEntryDto>> = signal(new Map());

  constructor(private explorerService: ExplorerService) {

  }

  doSurfaceIndex(): void {
    this.doIndexInternal('SURFACE');
  }

  doFullIndex(): void {
    this.doIndexInternal('FULL');
  }

  private doIndexInternal(method: 'FULL' | 'SURFACE'): void {
    this.explorerService
      .getStorageIndex(method)
      .toPromise()
      .then(res => {
        const _entries = res?.entries ?? [];
        const additions = new Map<string, StorageEntryDto>();
        _entries.forEach((entry: StorageEntryDto) => {
          additions.set(entry.uri, entry);
        });
        this.entries.update(it => new Map([...it, ...additions]));
      });
  }

  acquire(uris: Array<string>): void {

  }

  async load(uri: string): Promise<boolean> {
    const result = await this.explorerService
      .loadStorageEntry({uri})
      .toPromise();
    if (!result || !result.type || result.type === EntryLoadResultType.FAILED) {
      return false;
    }

    return true;
  }


}
