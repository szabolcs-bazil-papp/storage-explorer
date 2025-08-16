/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

import {Component, effect, inject, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterLink, RouterOutlet} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AppService, entry2icon, entry2url} from '../../app.service';
import {ScrollPanel} from 'primeng/scrollpanel';
import {Fieldset} from 'primeng/fieldset';
import {Tab, TabList, Tabs} from 'primeng/tabs';
import {Avatar} from 'primeng/avatar';
import {Button} from 'primeng/button';
import {StorageEntryDto} from '../../../api/se';

@Component({
  selector: 'inspector-container',
  imports: [
    RouterOutlet,
    ScrollPanel,
    Fieldset,
    Tabs,
    Tab,
    RouterLink,
    Avatar,
    TabList,
    Button
  ],
  template: `
    <p-scroll-panel
      [style]="{ height: '100%', width: '100%', 'padding-left': '1rem', 'padding-right': '1rem'}">
      <p-fieldset legend="Inspectors">
        <p-tabs [value]="tabVal()" scrollable class="tabz">
          <p-tablist>
            @for (entry of service.openInspectors(); track entry.uri) {
              <p-tab [value]="entry2url(entry)"
                     [routerLink]="entry2url(entry)"
                     class="inspector-tab">
                <p-avatar shape="circle" class="avatar-border"
                          [image]="entry2icon(entry)"></p-avatar>
                <span>{{ entry.typeName ?? (entry.schema + '/' + entry.name) }}</span>
              </p-tab>
              <p-button class="close-btn"  variant="text" icon="pi pi-times"
                        (onClick)="closeTab($event, entry)"></p-button>
            }
          </p-tablist>
        </p-tabs>
        <router-outlet></router-outlet>
      </p-fieldset>
    </p-scroll-panel>
  `,
  styles: `
    .tabz {
      width: 80vw;
      position: sticky;
      top: 0;
      z-index: 100;
    }

    .close-btn {
      max-width: 2rem;
      margin-left: -2rem;
    }

    ::ng-deep .p-tab-active {

    }
  `
})
export class InspectorContainer {

  protected readonly entry2url = entry2url;

  tabVal = signal('/app/inspect');
  readonly route = inject(ActivatedRoute);
  readonly router = inject(Router);

  readonly service = inject(AppService);

  constructor() {
    if (!this.route.snapshot.paramMap.has('id')) {
      const lastInspector = this.service.lastInspector();
      if (lastInspector) {
        this.router.navigateByUrl(entry2url(lastInspector), {onSameUrlNavigation: 'ignore'});
      }
    }

    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe(params => {
      const id = params.get('id');
      if (!id) {
        const lastInspector = this.service.lastInspector();
        if (lastInspector) {
          const url = entry2url(lastInspector);
          this.router.navigateByUrl(url, {onSameUrlNavigation: 'ignore'});
        }
      }
    });
    effect(() => {
      const lastInspector = this.service.lastInspector();
      if (lastInspector) {
        const url = entry2url(lastInspector);
        this.tabVal.set(url);
      } else {
        this.tabVal.set('/app/inspect');
      }
    });
  }



  protected readonly entry2icon = entry2icon;

  async closeTab(event: MouseEvent,  entry: StorageEntryDto) {
    // FIXME: This is needlessly convoluted...
    event.stopPropagation();
    const curr = this.service.lastInspector();
    if (curr?.uri === entry.uri) {
      const inspectedEntries = this.service.openInspectors();
      const idx = inspectedEntries.findIndex(e => e.uri === entry.uri);
      if (idx < 1 && inspectedEntries.length > 1) {
        const target = entry2url(inspectedEntries[1]);
        await this.router.navigateByUrl(target, {onSameUrlNavigation: 'ignore'})
          .then(() => this.service.openInspectors.update(it => it.filter(e => e.uri !== entry.uri)));
      } else if (idx < 1) {
        this.service.openInspectors.update(it => it.filter(e => e.uri !== entry.uri))
        await this.router.navigateByUrl('/app/dashboard');
      } else {
        const target = entry2url(inspectedEntries[idx - 1]);
        await this.router.navigateByUrl(target, {onSameUrlNavigation: 'ignore'})
          .then(() => this.service.openInspectors.update(it => it.filter(e => e.uri !== entry.uri)));
      }
    } else {
      this.service.openInspectors.update(it => it.filter(e => e.uri !== entry.uri));
    }

  }
}
