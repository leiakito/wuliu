import type { Component } from 'vue';

export interface NavItem {
  label: string;
  path: string;
  icon: Component;
  roles?: string[];
}
