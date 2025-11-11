declare global {
  interface Window {
    $crisp: Array<[string, ...unknown[]]>;
    CRISP_WEBSITE_ID: string;
  }
}

const pushCommand = (command: [string, ...unknown[]]) => {
  if (typeof window === 'undefined') return;
  window.$crisp = window.$crisp || [];
  window.$crisp.push(command);
};

export const enableCrisp = () => {
  pushCommand(['do', 'chat:show']);
};

export const disableCrisp = () => {
  pushCommand(['do', 'chat:hide']);
};
