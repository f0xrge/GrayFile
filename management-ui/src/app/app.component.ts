import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';

@Component({
  selector: 'gf-root',
  standalone: true,
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    MatButtonModule,
    MatIconModule,
    MatToolbarModule
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent {
  protected readonly navItems = [
    { label: 'Overview', route: '/overview', icon: 'space_dashboard' },
    { label: 'Clients', route: '/customers', icon: 'domain' },
    { label: 'API keys', route: '/api-keys', icon: 'vpn_key' },
    { label: 'Modeles & endpoints', route: '/models', icon: 'hub' }
  ];
}
