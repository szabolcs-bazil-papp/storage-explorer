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

import {computed, Directive, effect, inject, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AppService, url2uri} from '../../app.service';
import {StorageEntryDto} from "../../../api/se";

@Directive({})
export class AbstractInspector {

  readonly route = inject(ActivatedRoute);

  readonly identifier = signal(this.route.snapshot.paramMap.get('id') ?? 'No ID');
  readonly service = inject(AppService);

  readonly entry = computed<StorageEntryDto | undefined>(() => {
    const id = this.identifier();
    if (!id) {
      return;
    }

    console.log('Getting entry for id: ', id);
    const entries = this.service.entries();
    const uri = url2uri(id);
    console.log('Getting entry for URI: ', uri);
    return entries[uri];
  });

  constructor() {
    console.log('Inspector constructor called');
    this.subscribeToParamsChanged();
    effect(() => {
      console.log('Entry changed for adding to inspector');
      const _entry = this.entry();
      if (!_entry) {
        return;
      }

      const openInspectors = this.service.openInspectors();
      console.log('Open inspectors: ', openInspectors);
      if (openInspectors.length === 0 || !openInspectors.some(e => e.uri === _entry.uri)) {
        console.log('New entry added to open inspectors: ', _entry);
        this.service.openInspectors.update(it => [...it, _entry]);
      }
    })
    effect(() => {
      const id = this.identifier();
      if (id) {
        this.service.lastInspector.set('/' + this.route.snapshot.pathFromRoot.map(it => it.url.join('/')).join('/'));
      }
    });
  }

  protected subscribeToParamsChanged() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe(params => {
      this.identifier.update(it => {
        console.log('Params changed.');
        const id = params.get('id');
        return id!;
      });
    })
  }
}
