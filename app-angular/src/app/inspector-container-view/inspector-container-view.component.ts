import {Component, OnDestroy, signal, WritableSignal} from '@angular/core';
import {StorageIndexService} from '../services/storage-index.service';
import {VisualisationService} from '../services/visualisation.service';
import {TabsModule} from 'primeng/tabs';
import {StorageEntryDto} from '../../api/se';
import {Subscription} from 'rxjs';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-inspector-container-view',
  imports: [CommonModule, TabsModule],
  standalone: true,
  templateUrl: './inspector-container-view.component.html',
  styleUrl: './inspector-container-view.component.css',
})
export class InspectorContainerViewComponent implements OnDestroy {

  entries: Array<StorageEntryDto> = [];
  currentEntry: WritableSignal<string | undefined> = signal(undefined);
  private selectionSubscription: Subscription;

  constructor(public storageIndexService: StorageIndexService,
              public visualisationService: VisualisationService) {
    this.selectionSubscription = storageIndexService.entrySelection.subscribe((entry) => {
      this.selectEntry(entry);
    })
  }

  ngOnDestroy(): void {
    this.selectionSubscription.unsubscribe();
  }

  private selectEntry(entry: StorageEntryDto) {
    const present = this.entries.find((e) => e.uri === entry.uri);
    if (!present) {
      this.entries.push(entry);
    }
    this.currentEntry.set(entry.uri);
  }
}
