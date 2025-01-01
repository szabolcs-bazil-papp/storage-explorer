import {Injectable, signal, WritableSignal} from '@angular/core';
import {EntryLoadResultType, ExplorerService, StorageEntryDto} from '../../api/se';
import {Subject} from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class StorageIndexService {

  entries: WritableSignal<Map<string, StorageEntryDto>> = signal(new Map());
  entrySelection: Subject<StorageEntryDto> = new Subject();

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

  entrySelected(uri: string) {
    const entry = this.entries().get(uri)
    if (entry) {
      this.entrySelection.next(entry);
    } else {
      console.log('fooo!');
    }
  }


}
