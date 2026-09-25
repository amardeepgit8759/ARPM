import type { Role } from '@/api/schemas';

/**
 * Every destination in the app, per role, in menu order.
 *
 * A destination whose module does not exist yet carries `pending`: one honest sentence about
 * what it will do and what it is waiting on. Its route renders that sentence and nothing else,
 * never a mock-up with sample numbers. Building the module means deleting `pending` and giving
 * the route a real page.
 */
export interface Destination {
  to: string;
  label: string;
  pending?: string;
}

export const STUDENT_DESTINATIONS: readonly Destination[] = [
  { to: '/dashboard', label: 'Dashboard' },
  {
    to: '/setup',
    label: 'Setup',
    pending:
      'Choose your target company and role and how many weeks you have to prepare. Opens once companies and roles have been configured.',
  },
  {
    to: '/assessment',
    label: 'Assessment',
    pending:
      'Self-assessment, quick tests and topic tests, marked automatically. Opens once a reviewed question bank has been added.',
  },
  {
    to: '/analysis',
    label: 'Analysis',
    pending:
      'Your readiness, competency profile and gaps against your target, with how each figure was calculated. Opens once the scoring rules are finalised.',
  },
  {
    to: '/roadmap',
    label: 'Roadmap',
    pending:
      'A week-by-week plan built from your prioritised gaps, rebuilt after every assessment. Opens with gap analysis.',
  },
  {
    to: '/study',
    label: 'Study',
    pending: 'The topics on your roadmap, with learning resources and practice. Opens with the roadmap.',
  },
  {
    to: '/progress',
    label: 'Progress',
    pending:
      'How your readiness has changed across assessments. Opens once assessments are being scored.',
  },
  { to: '/profile', label: 'Profile' },
];

export const ADMIN_DESTINATIONS: readonly Destination[] = [
  { to: '/admin', label: 'Admin dashboard' },
  {
    to: '/admin/companies',
    label: 'Companies',
    pending: 'Add, edit and retire the companies students can target.',
  },
  {
    to: '/admin/roles',
    label: 'Roles',
    pending: 'Manage the job roles offered for each company.',
  },
  {
    to: '/admin/requirements',
    label: 'Requirements',
    pending:
      'Versioned skill requirements, thresholds and criticality for each company-role pair.',
  },
  {
    to: '/admin/questions',
    label: 'Questions',
    pending: 'Author questions and map each one to a domain, skill and sub-skill.',
  },
  {
    to: '/admin/taxonomy',
    label: 'Taxonomy',
    pending: 'Domains, skills, sub-skills and prerequisites, edited as drafts and then published.',
  },
  {
    to: '/admin/roadmap-rules',
    label: 'Roadmap rules',
    pending: 'Topic effort, ordering rules and priority rules used to build roadmaps.',
  },
  {
    to: '/admin/analytics',
    label: 'Analytics',
    pending:
      'Aggregate assessment, readiness and progress statistics. Opens once assessments are being scored.',
  },
];

/** Reachable by either role, but kept out of the admin menu, which the brief fixes at eight entries. */
export const ADMIN_ACCOUNT_DESTINATION: Destination = { to: '/profile', label: 'Your profile' };

export function destinationsFor(role: Role): readonly Destination[] {
  return role === 'ADMIN' ? ADMIN_DESTINATIONS : STUDENT_DESTINATIONS;
}

export function homePathFor(role: Role): string {
  return role === 'ADMIN' ? '/admin' : '/dashboard';
}
