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

import {Component, computed, inject, signal} from '@angular/core';
import {StorageEntryType} from '../../api/se';
import {Button} from 'primeng/button';
import {RouterLink, RouterLinkActive} from '@angular/router';
import {Avatar} from 'primeng/avatar';
import {Tooltip} from 'primeng/tooltip';
import {AppService, entry2url} from '../app.service';

export enum TreeItemType { ICO = "ICO", IMG = "IMG" }

export enum TreeItemQualifier { SIMPLE = "SIMPLE", COLLECTION = "COLLECTION", SCHEMA = "SCHEMA"}

export interface IconTreeItem {
  type: TreeItemType.ICO;
  qualifier: TreeItemQualifier.SIMPLE;
  id: string;
  icon: string;
  label: string;
  route: string;
}

export interface ImageTreeItem {
  type: TreeItemType.IMG;
  qualifier: TreeItemQualifier.SIMPLE;
  id: string;
  image: string;
  label: string;
  route: string;
}

export interface ListTreeItem {
  type: TreeItemType.IMG;
  entryType: StorageEntryType.LIST;
  qualifier: TreeItemQualifier.COLLECTION;
  image: 'list.png',
  id: string;
  label: string;
  route: string;
}

export interface MapTreeItem {
  type: TreeItemType.IMG;
  entryType: StorageEntryType.MAP;
  qualifier: TreeItemQualifier.COLLECTION;
  image: 'map.png',
  id: string;
  label: string;
  route: string;
}

export interface SequenceTreeItem {
  type: TreeItemType.IMG,
  entryType: StorageEntryType.SEQUENCE
  qualifier: TreeItemQualifier.COLLECTION;
  image: 'sequence.png',
  id: string;
  label: string;
  route: string;
}

export interface SchemaTreeItem {
  type: TreeItemType.IMG,
  image: 'db_fs.png',
  qualifier: TreeItemQualifier.SCHEMA;
  id: string;
  label: string;
  children: TypeTreeItem[];
}

export interface TypeTreeItem {
  type: TreeItemType.IMG,
  image: 'db_fs.png',
  id: string;
  children: ObjectTreeItem[];
  label: string;
}

export interface ObjectTreeItem {
  type: TreeItemType.IMG,
  image: 'object.png',
  entryType: StorageEntryType.OBJECT,
  id: string;
  label: string;
  route: string;
  children: StorageEntryTreeItem[];
}

export type StorageEntryTreeItem = ObjectTreeItem | ListTreeItem | MapTreeItem | SequenceTreeItem;
export type TreeItem =
  IconTreeItem
  | ImageTreeItem
  | SchemaTreeItem
  | TypeTreeItem
  | StorageEntryTreeItem;
export type RootTreeItem = SimpleTreeItem | CollectionTreeItem | SchemaTreeItem;
export type SimpleTreeItem = IconTreeItem | ImageTreeItem;
export type CollectionTreeItem = ListTreeItem | MapTreeItem | SequenceTreeItem;


@Component({
  selector: 'app-tree',
  template: `
    <div [class]="expandedHoriz() ? 'tree-container wide' : 'tree-container narrow'">
      <div class="tree-controls">
        @if (expandedHoriz()) {
          <span>Tree</span>
          <span class="spacer"></span>
        }
        <p-button
          [icon]="expandedVert() ? 'pi pi-arrow-down-left-and-arrow-up-right-to-center' : 'pi pi-arrow-up-right-and-arrow-down-left-from-center'"
          variant="text"
          (onClick)="toggleVerticalExpansion()"
          pTooltip="Expand/Collapse"></p-button>
        <p-button icon="pi pi-spinner-dotted" variant="text"
                  pTooltip="Load Entry"></p-button>
      </div>
      <div [class]="expandedHoriz() ? 'separator' : undefined"></div>
      <div class="my-tree">
        @for (root of simpleRoots; track root.id) {
          <div class="my-tree-root-container">
            <a [routerLink]="root.route" class="tree-link tree-root" routerLinkActive="active">
              @if (root.type === TreeItemType.ICO) {
                <p-avatar shape="circle" class="avatar-border"
                          [icon]="root.icon"
                          [label]="root.icon.length < 1 ? root.label.charAt(0) : undefined"></p-avatar>
                @if (expandedHoriz()) {
                  <span>{{ root.label }}</span>
                }
              } @else {
                <p-avatar shape="circle" class="avatar-border"
                          [image]="root.image"
                          [label]="root.image.length < 1 ? root.label.charAt(0) : undefined"></p-avatar>
                @if (expandedHoriz()) {
                  <span>{{ root.label }}</span>
                }
              }
            </a>
          </div>
        }

        @for (root of collectionRoots(); track root.id) {
          <div class="my-tree-root-container">
            <a [routerLink]="root.route" class="tree-link tree-root" routerLinkActive="active">
              <p-avatar shape="circle" class="avatar-border"
                        [image]="root.image"
                        [label]="root.image.length < 1 ? root.label.charAt(0) : undefined"></p-avatar>
              @if (expandedHoriz()) {
                <span>{{ root.label }}</span>
              }
            </a>
          </div>
        }

        @for (root of schemaRoots(); track root.id) {
          <div class="my-tree-root-container">
            <div class="tree-link tree-root">
              <p-avatar shape="circle" class="avatar-border"
                        [image]="root.image"
                        [label]="root.image.length < 1 ? root.label.charAt(0) : undefined"></p-avatar>
              @if (expandedHoriz()) {
                <span>{{ root.label }}</span>
              }
            </div>
            @for (t of root.children; track t.id) {
              <div
                [class]="expandedVert() ? 'tree-link tree-type' : 'tree-link tree-type hidden'">
                <p-avatar shape="circle" class="avatar-border"
                          [image]="t.image"
                          [label]="t.image.length < 1 ? t.label.charAt(0) : undefined"></p-avatar>
                @if (expandedHoriz()) {
                  <span>{{ t.label }}</span>
                }
              </div>
              @for (o of t.children; track o.id) {
                <a [routerLink]="o.route"
                   [class]="expandedVert() ? 'tree-link tree-object' : 'tree-link tree-object hidden'"
                   routerLinkActive="active">
                  <p-avatar shape="circle" class="avatar-border"
                            [image]="o.image"
                            [label]="o.image.length < 1 ? o.image.charAt(0) : undefined"></p-avatar>
                  @if (expandedHoriz()) {
                    <span>{{ o.label }}</span>
                  }
                </a>
              }
            }
          </div>
        }
        <span class="spacer"></span>
        <div [class]="expandedHoriz() ?'separator' : undefined"></div>
        <div class="expansion-control">
          <p-button variant="text"
                    [label]="expandedHoriz() ? 'Collapse' : undefined"
                    [icon]="expandedHoriz() ? 'pi pi-chevron-left' : 'pi pi-chevron-right'"
                    iconPos="right"
                    (onClick)="toggleHorizontalExpansion()">

          </p-button>
        </div>
      </div>
    </div>
  `,
  imports: [
    Button,
    RouterLink,
    RouterLinkActive,
    Avatar,
    Tooltip
  ],
  styles: `
    .tree-container {
      display: flex;
      flex-direction: column;
      border-right: 1px solid var(--p-primary-color);
      gap: 1rem;
      background-color: var(--p-surface-50);
      overflow: auto;
      height: 100%;
      max-height: calc(100vh - 80px);
    }

    .my-app-dark .tree-container {
      background-color: var(--p-surface-800);
    }

    .wide {
      width: 20em;
    }

    .narrow {
      width: 6em;
    }

    .my-tree {
      display: flex;
      flex-direction: column;
      flex-grow: 1;
    }

    .separator {
      margin: 0 10px;
      height: 1px;
      background-color: rgba(0, 0, 0, 0.1);
      display: flex;
      flex-shrink: 0;
    }

    .expansion-control {
      display: flex;
      justify-content: flex-end;
      bottom: 0;
      position: sticky;
      z-index: 100;
    }

    .padded-separator {
      margin-bottom: 0.5rem;
    }

    .tree-link {
      display: flex;
      align-items: center;
      text-decoration: none;
      transition: all 0.2s ease;
      gap: 0.5rem;
      color: black;
      padding: 0.5rem 1rem;
    }

    .my-tree-root-container {
      border-left: 5px solid rgba(from var(--p-primary-color) r g b / 0);
    }

    .my-tree-root-container:has(a.tree-link.active) {
      background-color: var(--p-surface-200);

      border-left: 5px solid rgba(from var(--p-primary-color) r g b / 0.6);
    }

    .my-tree-root-container:has(a.tree-link.active) > .tree-root {
      font-weight: bold;
    }

    a.tree-type.active {
      font-weight: 700;
    }

    .tree-type {
      padding-left: 1.5rem;
    }

    .tree-object {
      padding-left: 2.5rem;
    }

    .hidden {
      display: none;
    }

    .my-app-dark .my-tree-root-container:has(a.tree-link.active) {
      background-color: var(--p-surface-700);
    }

    .tree-link.active {
      background-color: var(--p-primary-200);
      border-left: 5px solid var(--p-primary-color);
    }

    .my-app-dark .tree-link.active {
      background-color: var(--p-primary-700);
    }

    .my-app-dark .tree-link {
      color: white
    }

    .tree-root {
      padding-bottom: 1rem;
    }

    .tree-controls {
      display: flex;
      align-items: center;
      padding-top: 1rem;
      font-weight: 700;
      font-size: 1.2rem;
      padding-left: 0.5rem;
    }
  `
})
export class AppTree {

  readonly simpleRoots: Array<SimpleTreeItem> = [
    {
      id: 'dashboard',
      type: TreeItemType.ICO,
      qualifier: TreeItemQualifier.SIMPLE,
      icon: 'pi pi-home',
      label: 'Dashboard',
      route: '/app/dashboard'
    },
    {
      id: 'arc-script',
      type: TreeItemType.ICO,
      qualifier: TreeItemQualifier.SIMPLE,
      icon: 'pi pi-code',
      label: 'ArcScript',
      route: '/app/arc-script'
    },
    {
      id: 'inspector',
      type: TreeItemType.ICO,
      qualifier: TreeItemQualifier.SIMPLE,
      icon: 'pi pi-compass',
      label: 'Inspector',
      route: '/app/inspect'
    }
  ];

  service = inject(AppService);

  collectionRoots = computed(() => {
    const entries = this.service.entries();
    const result: Array<CollectionTreeItem> = [];
    for (const uri in entries) {
      const e = entries[uri];
      switch (e.type) {
        case StorageEntryType.LIST:
          result.push({
            id: e.uri,
            type: TreeItemType.IMG,
            entryType: StorageEntryType.LIST,
            qualifier: TreeItemQualifier.COLLECTION,
            image: "list.png",
            label: `${e.schema} / ${e.name}`,
            route: entry2url(e),
          });
          break;
        case StorageEntryType.MAP:
          result.push({
            id: e.uri,
            type: TreeItemType.IMG,
            entryType: StorageEntryType.MAP,
            qualifier: TreeItemQualifier.COLLECTION,
            image: "map.png",
            label: `${e.schema} / ${e.name}`,
            route: entry2url(e),
          });
          break;
        case StorageEntryType.SEQUENCE:
          result.push({
            id: e.uri,
            type: TreeItemType.IMG,
            entryType: StorageEntryType.SEQUENCE,
            qualifier: TreeItemQualifier.COLLECTION,
            image: "sequence.png",
            label: `${e.schema} / ${e.name}`,
            route: entry2url(e),
          });
          break;
      }
    }
    return result;
  });

  schemaRoots = computed<Array<SchemaTreeItem>>(() => {
    const entries = this.service.entries();
    const nodesByName = new Map<string, SchemaTreeItem>();
    for (let uri in entries) {
      const e = entries[uri];
      if (e.type === StorageEntryType.OBJECT) {
        const schema = e.schema;
        const type = e.typeName!;

        let schemaNode: SchemaTreeItem;
        if (nodesByName.has(schema)) {
          schemaNode = nodesByName.get(schema)!;
        } else {
          schemaNode = {
            id: schema,
            type: TreeItemType.IMG,
            qualifier: TreeItemQualifier.SCHEMA,
            image: "db_fs.png",
            label: schema,
            children: [],
          };
          nodesByName.set(schema, schemaNode);
        }

        let typeNode = schemaNode.children.find(it => it.id === type);
        if (!typeNode) {
          typeNode = {
            id: type,
            type: TreeItemType.IMG,
            image: "db_fs.png",
            label: type,
            children: [],
          };
          schemaNode.children.push(typeNode);
        }
        typeNode.children.push({
          id: uri,
          route: entry2url(e),
          entryType: StorageEntryType.OBJECT,
          type: TreeItemType.IMG,
          image: "object.png",
          label: e.name.substring(0, 7) + '...',
          children: []
        });
      }
    }
    const result=  Array.from(nodesByName.values()).sort((a, b) => a.label.localeCompare(b.label));
    console.log('Calculated schema nodes: ', result);
    return result;
  });

  expandedHoriz = signal<boolean>(true);
  expandedVert = signal<boolean>(true);

  protected readonly TreeItemType = TreeItemType;

  toggleHorizontalExpansion() {
    this.expandedHoriz.update(it => !it);
  }

  toggleVerticalExpansion() {
    this.expandedVert.update(it => !it);
  }
}
