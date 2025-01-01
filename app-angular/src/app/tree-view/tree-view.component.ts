import {AfterViewInit, Component, computed, Signal, ViewChild} from '@angular/core';
import {ScrollPanel} from 'primeng/scrollpanel';
import {Tree} from 'primeng/tree';
import {StorageIndexService} from '../services/storage-index.service';
import {PrimeIcons, TreeNode} from 'primeng/api';
import {StorageEntryDto, StorageEntryType} from '../../api/se';

@Component({
  selector: 'app-tree-view',
  imports: [
    ScrollPanel,
    Tree,
  ],
  templateUrl: './tree-view.component.html',
  styleUrl: './tree-view.component.css',
})
export class TreeViewComponent implements AfterViewInit {

  nodes: Signal<Array<TreeNode>> = computed(() => {
    const entries = this.storageIndexService.entries();
    console.log('Tree View: entries incoming -> ', entries);
    return this.processEntries([...entries.values()]);
  });

  constructor(private storageIndexService: StorageIndexService) {
  }

  ngAfterViewInit(): void {
  }

  onNodeSelected(event: any): void {
    const uri = event?.node?.data;
    if (!uri) return;
    this.storageIndexService.entrySelected(uri as string);
  }

  private processEntries(entries: Array<StorageEntryDto>): Array<TreeNode> {
    const result: Array<TreeNode> = [];
    const entriesByType = new Map<StorageEntryType, Array<StorageEntryDto>>();
    const scopedEntriesByHost = new Map<string, Array<StorageEntryDto>>();
    for (const entry of entries) {
      if (entry.scopeHost) {
        const host = scopedEntriesByHost.get(entry.scopeHost);
        if (!host) {
          scopedEntriesByHost.set(entry.scopeHost, [entry]);
        } else {
          host.push(entry);
        }
        continue;
      }

      const type = entry.type;
      const v = entriesByType.get(type);
      if (!v) {
        entriesByType.set(type, [entry]);
      } else {
        v.push(entry);
      }
    }

    result.push(...this.processLists(entriesByType.get(StorageEntryType.LIST) ?? []));
    result.push(...this.processMaps(entriesByType.get(StorageEntryType.MAP) ?? []));
    result.push(...this.processSequences(entriesByType.get(StorageEntryType.SEQUENCE) ?? []));
    result.push(...this.processObjects(entriesByType.get(StorageEntryType.OBJECT) ?? [], scopedEntriesByHost));
    return result;
  }

  private processCollections(cs: Array<StorageEntryDto>, icon: string): Array<TreeNode> {
    return cs
      .sort((a, b) => {
        const aName = a.schema + a.name;
        const bName = b.schema + b.name;
        return aName.localeCompare(bName);
      })
      .map(it => {
        return {
          key: it.uri,
          label: it.schema + ' / ' + it.name,
          icon,
          data: it.uri,
        } as TreeNode;
      });
  }

  private processLists(cs: Array<StorageEntryDto>): Array<TreeNode> {
    return this.processCollections(cs, PrimeIcons.LIST);
  }

  private processMaps(cs: Array<StorageEntryDto>): Array<TreeNode> {
    return this.processCollections(cs, PrimeIcons.MAP);
  }

  private processSequences(cs: Array<StorageEntryDto>): Array<TreeNode> {
    return this.processCollections(cs, PrimeIcons.SORT_NUMERIC_DOWN);
  }

  private processObjects(objects: Array<StorageEntryDto>,
                         scopedEntriesByHost: Map<string, Array<StorageEntryDto>>): Array<TreeNode> {
    const bySchema = new Map<string, Array<StorageEntryDto>>();
    for (const entry of objects) {
      const schema = entry.schema;
      const v = bySchema.get(schema);
      if (!v) {
        bySchema.set(schema, [entry]);
      } else {
        v.push(entry);
      }
    }

    const result: Array<TreeNode> = [];
    for (const schema of bySchema.keys()) {
      const schemaNode: TreeNode = {
        key: schema,
        label: schema,
      };
      schemaNode.children = this.createTypeNodes(bySchema.get(schema)!, schemaNode, scopedEntriesByHost);
      schemaNode.leaf = !schemaNode.children || schemaNode.children.length === 0;
      result.push(schemaNode);
    }
    console.log('Schema nodes: ', result);
    return result;
  }

  private createTypeNodes(objects: Array<StorageEntryDto>, parent: TreeNode,
                          scopedEntriesByHost: Map<string, Array<StorageEntryDto>>): Array<TreeNode> {
    const byTypeName = new Map<string, Array<StorageEntryDto>>();
    for (const entry of objects) {
      const typeName = entry.typeName!;
      console.log('typeName: ', typeName);
      const v = byTypeName.get(typeName);
      if (!v) {
        byTypeName.set(typeName, [entry]);
        console.log('Set entry to type', entry, typeName);
      } else {
        v.push(entry);
        console.log('Added entry to type', entry, typeName);
      }
    }

    const result: Array<TreeNode> = [];
    for (const typeName of byTypeName.keys()) {
      const typeNode: TreeNode = {
        key: typeName,
        label: typeName,
        parent,
      };
      typeNode.children = this.createObjectNodes(byTypeName.get(typeName)!, typeNode, scopedEntriesByHost);
      typeNode.leaf = !typeNode.children || typeNode.children.length === 0;
      result.push(typeNode);
    }
    return result;
  }

  private createObjectNodes(objects: Array<StorageEntryDto>, parent: TreeNode,
                            scopedEntriesByHost: Map<string, Array<StorageEntryDto>>): Array<TreeNode> {
    return objects
      .map(it => {
        const objectNode: TreeNode = {
          key: it.uri,
          label: it.name,
          parent,
          data: it.uri,
          icon: PrimeIcons.BOX,
        };
        const scopedEntries = scopedEntriesByHost.get(it.uri);
        if (scopedEntries && scopedEntries.length > 0) {
          const children: Array<TreeNode> = [];
          for (const scopedEntry of scopedEntries) {
            const type = scopedEntry.type;
            children.push({
              key: scopedEntry.uri,
              label: (type === StorageEntryType.OBJECT)
                ? scopedEntry.name
                : scopedEntry.schema + ' / ' + scopedEntry.name,
              parent: it,
              icon: type === StorageEntryType.LIST
                ? PrimeIcons.LIST
                : type === StorageEntryType.MAP
                  ? PrimeIcons.MAP
                  : PrimeIcons.BOX,
              data: scopedEntry.uri,
              leaf: true,
            });
          }
          objectNode.children = children;
          objectNode.leaf = !objectNode.children || objectNode.children.length === 0;
        }
        return objectNode;
      });
  }


}
