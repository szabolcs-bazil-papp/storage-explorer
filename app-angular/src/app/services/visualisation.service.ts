import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class VisualisationService {

  showGraph: boolean = false;
  showInspectors: boolean = true;

  constructor() { }
}
