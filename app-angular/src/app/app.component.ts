import {AfterViewInit, Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {ExplorerService} from '../api/se';
import {NgIf} from '@angular/common';
import {TreeViewComponent} from './tree-view/tree-view.component';
import {HeaderComponent} from './header/header.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NgIf, TreeViewComponent, HeaderComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements AfterViewInit {
  title = 'app-angular';
  content?: string;

  constructor(private explorerService: ExplorerService) {
  }

  ngAfterViewInit(): void {
  }
}
