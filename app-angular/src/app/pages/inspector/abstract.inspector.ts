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
import {EntryLoadResult, EntryLoadResultType, StorageEntryDto} from "../../../api/se";

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

    const entries = this.service.entries();
    const uri = url2uri(id);
    const cachedEntry = entries[uri];
    if (!cachedEntry) {
      this.service.performAcquire(uri);
    }

    return cachedEntry;
  });

  readonly loadResult = signal<EntryLoadResult>({type: EntryLoadResultType.FAILED, versions: []});

  readonly v = signal(0);

  constructor() {
    this.subscribeToParamsChanged();
    effect(() => {
      const _entry = this.entry();
      if (!_entry) {
        return;
      }

      const openInspectors = this.service.openInspectors();
      if (openInspectors.length === 0 || !openInspectors.some(e => e.uri === _entry.uri)) {
        this.service.openInspectors.update(it => [...it, _entry]);
      }
      this.service.lastInspector.set(_entry);
    });
    effect(() => {
      const entry = this.entry();
      if (!entry) {
        return;
      }

      this.service.load(entry).then(res => {
        this.loadResult.set(res);
        this.v.set(res.versions.length - 1);
      });
    });
  }

  protected subscribeToParamsChanged() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe(params => {
      this.identifier.update(it => {
        const id = params.get('id');
        return id!;
      });
    })
  }
}
