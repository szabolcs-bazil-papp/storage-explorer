import { TestBed } from '@angular/core/testing';

import { StorageIndexService } from './storage-index.service';

describe('StorageIndexService', () => {
  let service: StorageIndexService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(StorageIndexService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
