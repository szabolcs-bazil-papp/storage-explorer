/**
 * Storage Explorer Embedded RESTful API
 *
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
import { EntryVersionDto } from './entryVersionDto';
import { EntryLoadResultType } from './entryLoadResultType';
import { StorageEntryDto } from './storageEntryDto';


export interface EntryLoadResult { 
    type?: EntryLoadResultType;
    entry?: StorageEntryDto;
    versions: Array<EntryVersionDto>;
}


