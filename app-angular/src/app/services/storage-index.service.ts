import {Injectable, signal, WritableSignal} from '@angular/core';
import {EntryLoadResult, EntryLoadResultType, ExplorerService, StorageEntryDto} from '../../api/se';
import {Subject} from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class StorageIndexService {

  entries: WritableSignal<Map<string, StorageEntryDto>> = signal(new Map());
  entrySelection: Subject<StorageEntryDto> = new Subject();

  entryDetails: WritableSignal<Map<string, EntryLoadResult>> = signal(new Map());

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

  async load(uri: string): Promise<boolean> {
    const result = await this.explorerService
      .loadStorageEntry({uri})
      .toPromise();
    if (!result || !result.type || result.type === EntryLoadResultType.FAILED) {
      return false;
    }

    const entry = result.entry;
    if (entry) {
      this.entries.update(it => it.set(uri, entry));
    }

    this.entryDetails.update(it => it.set(uri, result));
    return true;
  }

  async getDetailsOfEntry(uri: string): Promise<EntryLoadResult | undefined> {
    const details = this.entryDetails().get(uri);
    if (!details) {
      const yes = await this.load(uri);
      return yes ? this.getDetailsOfEntry(uri) : undefined;
    }

    return details;
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
