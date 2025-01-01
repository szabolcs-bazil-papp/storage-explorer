import {AfterViewInit, Component} from '@angular/core';
import {ExplorerService} from '../api/se';
import {NgIf} from '@angular/common';
import {TreeViewComponent} from './tree-view/tree-view.component';
import {HeaderComponent} from './header/header.component';
import {VisualisationService} from './services/visualisation.service';
import {GraphViewComponent} from './graph-view/graph-view.component';
import {
  InspectorContainerViewComponent,
} from './inspector-container-view/inspector-container-view.component';

@Component({
  selector: 'app-root',
  imports: [NgIf, TreeViewComponent, HeaderComponent, GraphViewComponent, InspectorContainerViewComponent],
  templateUrl: './app.component.html',
  standalone: true,
  styleUrl: './app.component.css',
})
export class AppComponent implements AfterViewInit {
  title = 'app-angular';
  content?: string;

  constructor(private explorerService: ExplorerService, public visualisationService: VisualisationService) {
  }

  ngAfterViewInit(): void {
  }
}
