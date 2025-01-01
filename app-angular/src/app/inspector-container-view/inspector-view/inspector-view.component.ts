import {Component, input, InputSignal, OnDestroy, OnInit} from '@angular/core';
import {
  EntryLoadResult,
  EntryLoadResultType,
  EntryVersionDto,
  StorageEntryDto,
  StorageEntryType,
} from '../../../api/se';
import {StorageIndexService} from '../../services/storage-index.service';
import {NgIf} from '@angular/common';
import {TableModule} from 'primeng/table';
import {PrismComponent} from '../../prism-module/prism.component';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';

@Component({
  selector: 'app-inspector-view',
  imports: [
    NgIf,
    TableModule,
    PrismComponent,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
  ],
  templateUrl: './inspector-view.component.html',
  styleUrl: './inspector-view.component.css',
})
export class InspectorViewComponent implements OnInit, OnDestroy {

  protected readonly StorageEntryType = StorageEntryType;
  entry: InputSignal<StorageEntryDto> = input.required();
  err?: Err;
  details?: EntryLoadResult;

  currVersion: number = 0;

  constructor(private storageIndexService: StorageIndexService) {
  }

  ngOnInit(): void {
    this.storageIndexService.getDetailsOfEntry(this.entry().uri)
      .then(
        ok => {
          if (!ok) {
            this.err = {
              msg: 'Entry cannot be loaded!',
            };
          }

          this.details = ok;
        },
        err => {
          this.err = {
            msg: `${err}`,
          };
        });
  }

  isType(type: StorageEntryType): boolean {
    return type === this.details?.entry?.type;
  }

  stringifyObjectContent(v?: EntryVersionDto): string {
    if (!v) {
      v = this.details?.versions[0];
    }

    if (!v) {
      return '';
    }

    return JSON.stringify(v.objectAsMap, null, 2);
  }

  isSingleLoad(): boolean {
    return this.isLoadType(EntryLoadResultType.SINGLE);
  }

  isMultiLoad(): boolean {
    return this.isLoadType(EntryLoadResultType.MULTI);
  }

  isLoadType(type: EntryLoadResultType): boolean {
    return type === this.details?.type;
  }

  ngOnDestroy(): void {

  }


  protected readonly Map = Map;
}

export interface Err {
  msg: string;
}
