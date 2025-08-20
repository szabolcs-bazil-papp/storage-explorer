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

import {effect, inject, Injectable, signal, WritableSignal} from '@angular/core';
import {
  ArcScriptEvalResponse,
  EntryLoadResult,
  EntryLoadResultType,
  ExplorerService,
  StorageEntryDto,
  StorageEntryType
} from '../api/se';
import {lastValueFrom} from 'rxjs';
import {Router} from '@angular/router';
import {AbstractControl, ValidatorFn} from '@angular/forms';
import {MessageService} from 'primeng/api';

const MARKER_STORED_LIST = '/storedlist';
const MARKER_STORED_MAP = '/storedmap';
const MARKER_STORED_REF = '/storedRef';
const MARKER_STORED_SEQ = '/storedSeq';
const MARKERS = [MARKER_STORED_LIST, MARKER_STORED_MAP, MARKER_STORED_REF, MARKER_STORED_SEQ];
const REGEX_TIMESTAMP = /\d{4}\/\d{1,2}\/\d{1,2}\/\d{1,2}/;

function containsStoredCollectionIdentifier(s: string): boolean {
  return MARKERS.some(m => s.includes(m));
}

function containsTimestamp(s: string): boolean {
  return REGEX_TIMESTAMP.test(s);
}

/**
 * Checks if the given URI is valid for storage access purposes
 *
 * @param uri an URI string of dubious origin to check
 */
export function isUriValid(uri: string): boolean {
  if (uri.length < 4) {
    return false;
  }

  const schemaDelimiter = uri.indexOf(':/');
  if (schemaDelimiter < 1) {
    return false;
  }

  if (uri.endsWith('.') || uri.endsWith('.v')) {
    return false;
  }

  if (uri.includes('~')) {
    return false;
  }

  return (containsStoredCollectionIdentifier(uri) || containsTimestamp(uri));
}

export const VALIDATOR_URI: ValidatorFn = (control: AbstractControl) => {
  const val = control.value as string;
  return isUriValid(val) ? null : {invalidUri: "Invalid URI"};
}

/**
 * Enumerates entries by their URI.
 */
export interface EntryMap {
  [key: string]: StorageEntryDto;
}

/**
 * Determines the unique URL for inspecting a given entry.
 *
 * @param entry a storage entry to inspect
 */
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

/**
 * Determines the icon for a given entry based on its type.
 *
 * @param entry an entry to determine the icon for
 */
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

/**
 * Converts the unique inspector URL to the storage URI of the inspected entry.
 *
 * @param url the dynamic URL segment of the inspector
 */
export function url2uri(url: string) {
  return url.replace('\~', ':').replaceAll('\~', '/');
}

export const KEY_DARK_MODE = 'darkMode';

export interface Msg {
  summary: string;
  detail: string;
}

const MSG_LIFETIME = 3_000;

@Injectable({providedIn: 'root'})
export class AppService {

  /** All entries currently known to the application. */
  entries = signal<EntryMap>({});
  /** The current ArcScript text provided by the user. */
  scriptText: string = '\n'.repeat(10);
  /** The result of the last ArcScript evaluation. */
  scriptResult = signal<ArcScriptEvalResponse | undefined>(undefined);
  /** The last inspector URL that was opened. */
  lastInspector = signal<StorageEntryDto | undefined>(undefined);
  /** The list of inspectors currently open. */
  openInspectors = signal<Array<StorageEntryDto>>([]);

  isDark: WritableSignal<boolean>;

  private readonly api = inject(ExplorerService);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);

  constructor() {
    const _isDark = localStorage.getItem(KEY_DARK_MODE) === 'true';
    if (_isDark) {
      document.documentElement.classList.add('my-app-dark');
    }

    this.isDark = signal(_isDark);
    effect(() => {
      if (this.openInspectors().length === 0) {
        this.lastInspector.set(undefined);
      }
    });
  }

  /**
   * Performs an ArcScript evaluation with the current script text and updates the application state
   * accordingly.
   */
  async performExecuteScript() {
    let resp: ArcScriptEvalResponse;
    try {
      resp = await lastValueFrom(this.api.eval({script: this.scriptText}));
    } catch (error: any) {
      resp = (error.error as ArcScriptEvalResponse);
      resp.err ??= {
        msg: (error as any)?.message ?? 'Unknown error occurred.',
        col: 0,
        line: 0,
      };
    }

    if (resp.err) {
      this.msgErr({
        summary: 'Script Evaluation Error',
        detail: (resp.err.msg ?? 'Unknown error occurred.') + '\n' + resp.err.line + ':' + resp.err.col,
      });
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
        })
        .catch(() => {
          this.msgWarn({
            summary: 'Failed to fetch entries.',
            detail: 'Some entries may not be available.'
          })
        });

      this.scriptResult.update(() => resp);
    }
  }

  /**
   * Loads the contents of an entry.
   *
   * @param entry a storage entry to load
   */
  async load(entry: StorageEntryDto): Promise<EntryLoadResult> {
    return await lastValueFrom(this.api.loadStorageEntry({uri: entry.uri}))
      .catch(err => {
        this.msgErr({summary: 'Failed to load entry', detail: err.message});
        return {
          type: EntryLoadResultType.FAILED,
          entry,
          versions: []
        };
      });
  }

  /**
   * Acquires the storage entry corresponding to the provided URI, and issues navigation to its
   * inspector.
   *
   * @param uri a storage URI to acquire the entry for
   */
  async performAcquire(uri: string) {
    if (!isUriValid(uri)) {
      this.msgErr({summary: 'Invalid URI!', detail: uri})
      return;
    }

    await lastValueFrom(this.api.getStorageEntry({uris: [uri]}))
      .then(res => {
        if ((res.entries?.length ?? 0) !== 1) {
          this.msgErr({
            summary: 'Entry not found',
            detail: `Entry with URI '${uri}' not found.`
          });
          return null;
        } else {
          const entry = res.entries![0];
          this.entries.update(cache => {
            const nuCache = {...cache};
            nuCache[entry.uri] = entry;
            return nuCache;
          });
          return entry;
        }
      })
      .then(entry => {
        if (entry) {
          this.router.navigateByUrl(entry2url(entry));
        }
      })
      .catch(err => {
        this.msgErr({summary: 'Failed to acquire entry', detail: err.message});
      });
  }

  // -----------------------------------------------------------------------------------------------
  // Toast

  msgInfo(msg: Msg) {
    const {summary, detail} = msg;
    this.messageService.add({
      severity: 'info',
      summary,
      detail,
      life: MSG_LIFETIME,
      closable: true
    });
  }

  msgWarn(msg: Msg) {
    const {summary, detail} = msg;
    this.messageService.add({
      severity: 'warn',
      summary,
      detail,
      life: MSG_LIFETIME,
      closable: true
    });
  }

  msgErr(msg: Msg) {
    const {summary, detail} = msg;
    this.messageService.add({
      severity: 'error',
      summary,
      detail,
      life: MSG_LIFETIME,
      closable: true
    });
  }

  msgSuccess(msg: Msg) {
    const {summary, detail} = msg;
    this.messageService.add({
      severity: 'success',
      summary,
      detail,
      life: MSG_LIFETIME,
      closable: true
    });
  }

}
