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

import {inject, Injectable, signal} from '@angular/core';
import {
  ArcScriptEvalResponse,
  EntryLoadResult,
  ExplorerService,
  StorageEntryDto,
  StorageEntryType
} from '../api/se';
import {lastValueFrom} from 'rxjs';

export interface EntryMap {
  [key: string]: StorageEntryDto;
}

export function entry2url(entry: StorageEntryDto) {
  return `/app/inspect/${urlTypeQualifier(entry.type)}/${entry.uri.replace(':', '~').replaceAll('/', '~')}`;
}

function urlTypeQualifier(type: StorageEntryType) {
  switch (type) {
    case StorageEntryType.LIST:
      return 'l';
    case StorageEntryType.MAP:
      return 'm';
    case StorageEntryType.OBJECT:
      return 'o';
    case StorageEntryType.SEQUENCE:
      return 's';
  }
}

export function entry2icon(entry: StorageEntryDto) {
  switch (entry.type) {
    case StorageEntryType.LIST:
      return 'list.png';
    case StorageEntryType.MAP:
      return 'map.png';
    case StorageEntryType.OBJECT:
      return 'object.png';
    case StorageEntryType.SEQUENCE:
      return 'sequence.png';
  }
}

export function url2uri(url: string) {
  return url.replace('\~', ':').replaceAll('\~', '/');
}

@Injectable({providedIn: 'root'})
export class AppService {

  entries = signal<EntryMap>({});
  scriptText: string = '\n'.repeat(10);
  scriptResult = signal<ArcScriptEvalResponse | undefined>(undefined);
  lastInspector = signal<string | undefined>(undefined);
  openInspectors = signal<Array<StorageEntryDto>>([]);
  api = inject(ExplorerService);


  async performExecuteScript() {
    const resp = await lastValueFrom(this.api.eval({script: this.scriptText}));
    if (resp.err) {
      console.error(resp.err);
    } else {
      const uris = resp.resultSet.flatMap(it => {
        const uri = (it as any)['uri'];
        if (uri) {
          return [uri as string];
        } else {
          return [];
        }
      });
      lastValueFrom(this.api.getStorageEntry({uris}))
        .then(ok => {
          this.entries.update(cache => {
            if ((ok.entries?.length ?? 0) > 0) {
              const nuCache = {...cache};
              for (const e of ok.entries!) {
                nuCache[e.uri] = e;
              }
              return nuCache;
            }
            return cache;
          });
        });

      this.scriptResult.update(() => resp);
    }
  }

  async load(entry: StorageEntryDto): Promise<EntryLoadResult> {
   return await lastValueFrom(this.api.loadStorageEntry({ uri: entry.uri }));
  }


}
