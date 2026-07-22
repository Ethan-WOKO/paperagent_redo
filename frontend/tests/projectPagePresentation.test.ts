import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const pagePath = fileURLToPath(new URL('../src/views/ProjectPreviewPage.vue', import.meta.url));
const source = readFileSync(pagePath, 'utf8');

describe('Project page presentation contract', () => {
  it('keeps the frozen sidebar allocation while stabilizing every title row', () => {
    expect(source).toContain('.project-sidebar-section--projects { flex: 0 1 25%; min-height: 86px; }');
    expect(source).toContain('.project-sidebar-section--chats { flex: 0 1 25%; min-height: 86px; }');
    expect(source).toContain('.project-sidebar-section--file-browser { flex: 1 1 50%; min-height: 0; }');
    expect(source).toContain('flex: 0 0 32px; min-width: 0; height: 32px; display: flex; align-items: center;');
    expect(source).not.toContain('.project-sidebar-section--collapsed .project-sidebar-section__toggle');
  });

  it('uses the existing UI library chevron with fixed icon buttons and accessible labels', () => {
    expect(source).toContain("import { ChevronRightIcon } from 'naive-ui/es/_internal/icons';");
    expect(source.match(/<ChevronRightIcon \/>/g)?.length).toBeGreaterThanOrEqual(7);
    expect(source).toContain('class="project-chevron-button"');
    expect(source).toContain(":aria-label=\"sidebarSections.projects ? t('project.page.expandProjects') : t('project.page.collapseProjects')\"");
    expect(source).toContain(":aria-label=\"sidebarSections.conversations ? t('project.page.expandConversations') : t('project.page.collapseConversations')\"");
    expect(source).toContain(":aria-label=\"sidebarSections.files ? t('project.page.expandFiles') : t('project.page.collapseFiles')\"");
    expect(source).not.toMatch(/sidebarSections\.[a-z]+ \? '>' : 'v'/);
    expect(source).not.toContain('&gt;</span>');
  });

  it('has one composer, no Chat/Plan mode tabs, and one inspector navigation group', () => {
    expect(source.match(/v-model:value="chatInput"/g)).toHaveLength(1);
    expect(source).not.toContain('centerTab');
    expect(source).not.toContain('project plan conversation');
    expect(source.match(/class="project-utility-chip"/g)).toHaveLength(4);
    expect(source).not.toContain('@click="inspectorTab =');
  });

  it('renders Plans as collapsed execution cards without a second final answer', () => {
    expect(source).toContain('class="project-execution-card__details"');
    expect(source).not.toContain('class="project-execution-card__details" open');
    expect(source).toContain("t('project.result.details')");
    expect(source).toContain('planProgressLabel(item.plan)');
    expect(source).toContain('planUserStatus(item.plan)');
    expect(source).toContain("t('project.result.confirm')");
    expect(source).not.toContain('Final step synthesis');
    expect(source).not.toContain('projectPlanFinalAnswer');
  });

  it('prevents inspector pills and execution metadata from wrapping or overflowing', () => {
    expect(source).toContain('white-space: nowrap; cursor: pointer;');
    expect(source).toContain('.project-tabs__actions { width: 100%; flex-wrap: nowrap; }');
    expect(source).toContain('.project-execution-card__heading > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap;');
  });
});
