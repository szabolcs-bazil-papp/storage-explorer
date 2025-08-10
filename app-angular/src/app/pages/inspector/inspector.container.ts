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

import {Component, inject} from '@angular/core';
import {ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AppService, entry2icon, entry2url} from '../../app.service';
import {ScrollPanel} from 'primeng/scrollpanel';
import {Fieldset} from 'primeng/fieldset';
import {Tab, TabList, Tabs} from 'primeng/tabs';
import {Avatar} from 'primeng/avatar';

@Component({
  selector: 'inspector-container',
  imports: [
    RouterOutlet,
    ScrollPanel,
    Fieldset,
    Tabs,
    Tab,
    RouterLink,
    RouterLinkActive,
    Avatar,
    TabList
  ],
  template: `
    <p-scroll-panel
      [style]="{ height: '100%', width: '100%', 'padding-left': '1rem', 'padding-right': '1rem'}">
      <p-fieldset legend="Inspectors">
        <p-tabs value="/app/inspect" scrollable class="tabz">
          <p-tablist>
            @for (entry of service.openInspectors(); track entry.uri) {
              <p-tab [value]="entry.uri"
                     [routerLink]="entry2url(entry).substring('/app/inspect/'.length)"
                     class="inspector-tab"
                     routerLinkActive="active">
                <p-avatar shape="circle" class="avatar-border"
                          [image]="entry2icon(entry)"></p-avatar>
                <span>{{ entry.typeName }}</span>
              </p-tab>
            }
          </p-tablist>
        </p-tabs>
        <router-outlet></router-outlet>
      </p-fieldset>
    </p-scroll-panel>
  `,
  styles: `
    .tabz {
      max-width: 60vw;
    }
    .inspector-tab {

    }

    .active {

    }
  `
})
export class InspectorContainer {

  protected readonly entry2url = entry2url;

  readonly route = inject(ActivatedRoute);
  readonly router = inject(Router);

  readonly service = inject(AppService);

  constructor() {
    if (!this.route.snapshot.paramMap.has('id')) {
      const lastInspector = this.service.lastInspector();
      if (lastInspector) {
        this.router.navigateByUrl(lastInspector, {onSameUrlNavigation: 'ignore'});
      }
    }

    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe(params => {
      const id = params.get('id');
      if (!id) {
        const lastInspector = this.service.lastInspector();
        if (lastInspector) {
          this.router.navigateByUrl(lastInspector, {onSameUrlNavigation: 'ignore'});
        }
      }
    })
  }

  protected readonly entry2icon = entry2icon;
}
