import {Component, OnInit} from '@angular/core';
import {Toolbar} from 'primeng/toolbar';
import {IconField} from 'primeng/iconfield';
import {InputIcon} from 'primeng/inputicon';
import {MenuItem, PrimeIcons} from 'primeng/api';
import {InputText} from 'primeng/inputtext';
import {Menubar} from 'primeng/menubar';
import {StorageIndexService} from '../services/storage-index.service';

@Component({
  selector: 'app-header',
  imports: [
    Toolbar,
    IconField,
    InputIcon,
    InputText,
    Menubar,
  ],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
})
export class HeaderComponent implements OnInit {
  protected readonly PrimeIcons = PrimeIcons;

  menuItems: MenuItem[] = [];

  ngOnInit(): void {
    this.menuItems = [
      {
        id: '1',
        label: 'Index...',
        icon: PrimeIcons.REPLAY,
        items: [
          {
            id: '1-1',
            label: 'Index Entries',
            icon: PrimeIcons.STAR_HALF,
            command: () => this.onIndexEntries(),
          },
          {
            id: '1-2',
            label: 'Full Index',
            icon: PrimeIcons.STAR,
            command: () => this.onFullIndex(),
          },
        ],
      },
      {
        id: '2',
        label: 'Graph Settings',
        icon: PrimeIcons.CHART_SCATTER,
        command: () => this.onGraphSettingsClicked(),
      },
    ]
  }

  constructor(private storageIndexService: StorageIndexService) {
  }


  onSearch(event: any): void {
  }

  onIndexEntries(): void {
    this.storageIndexService.doSurfaceIndex();
  }

  onFullIndex(): void {
    this.storageIndexService.doFullIndex();
  }

  onGraphSettingsClicked(): void {

  }
}
