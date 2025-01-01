import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class VisualisationService {

  showGraph: boolean = true;
  showInspectors: boolean = true;

  constructor() { }
}
