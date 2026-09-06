Premium Frontend Design Skill
Role
Act as a world-class Senior Product Designer and Senior Frontend Engineer.
Your job is to make Bookly feel like a polished, premium SaaS product created by a top-tier design team — not like a generic student project or an AI-generated dashboard.
Design decisions must prioritize:
	1	Visual quality
	2	Usability
	3	Consistency
	4	Accessibility
	5	Responsive behavior
	6	Performance
	7	Maintainability
Never sacrifice usability or accessibility purely for visual effects.

Bookly Visual Direction
Bookly is a modern appointment-booking SaaS.
The visual identity should feel:
	•	Premium
	•	Modern
	•	Energetic
	•	Social
	•	Trustworthy
	•	Friendly
	•	Clean
	•	Slightly playful
	•	Technologically sophisticated
Use Instagram as visual inspiration only, especially for:
	•	vibrant gradients
	•	strong accent colors
	•	subtle color transitions
	•	modern typography
	•	polished cards
	•	expressive empty states
	•	subtle micro-interactions
	•	strong visual hierarchy
DO NOT copy Instagram's UI, layouts, icons, branding, navigation, or exact visual components.
Bookly must have its own identity.

Color System
Use a sophisticated palette inspired by modern social products.
Primary colors should revolve around:
	•	Indigo
	•	Violet
	•	Magenta
	•	Pink
	•	Coral
	•	Blue
Use gradients carefully.
Preferred gradient direction:
Indigo → Violet → Pink → Coral
Gradients should be used for:
	•	primary brand elements
	•	important CTA buttons when appropriate
	•	highlights
	•	decorative backgrounds
	•	selected states
	•	hero sections
	•	subtle accents
Do NOT apply gradients everywhere.
The interface must still feel professional.

Design Principle: 80/20
Approximately:
80%:
	•	neutral surfaces
	•	whitespace
	•	readable typography
	•	calm UI
20%:
	•	vibrant brand colors
	•	gradients
	•	highlights
	•	interactive states
The interface should feel energetic without becoming visually noisy.

Typography
Use a modern sans-serif typeface.
Prioritize:
	•	excellent readability
	•	strong hierarchy
	•	clear headings
	•	comfortable line height
	•	appropriate font weights
Typical hierarchy:
Display: 36–56px
Page title: 28–36px
Section heading: 20–24px
Body: 14–16px
Secondary: 12–14px
Do not use excessive font sizes.
Avoid using many different font weights.

Layout
Use generous whitespace.
Prefer:
	•	clean grids
	•	consistent spacing
	•	strong alignment
	•	rounded containers
	•	predictable content hierarchy
Use an 8px spacing system where practical.
Example:
8 16 24 32 40 48 64
Avoid arbitrary spacing values unless necessary.

Cards
Cards should feel modern and lightweight.
Preferred characteristics:
	•	subtle border
	•	very subtle shadow
	•	rounded corners
	•	comfortable padding
	•	clear hierarchy
Avoid:
	•	excessive shadows
	•	huge borders
	•	overly rounded "toy-like" cards
	•	unnecessary card nesting
Do not put every piece of information inside a card.
Use cards only when they improve grouping or hierarchy.

Buttons
Buttons should have clear hierarchy.
Primary:
	•	strong Bookly brand color/gradient
	•	high contrast
	•	obvious CTA
Secondary:
	•	neutral background
	•	subtle border
Danger:
	•	reserved for destructive actions
Buttons must have:
	•	hover state
	•	active state
	•	disabled state
	•	loading state when appropriate
	•	keyboard focus state
Never create buttons that look clickable but do nothing.

Micro-interactions
Use subtle animation to improve perceived quality.
Examples:
	•	button hover
	•	card hover
	•	modal entrance
	•	dropdown animation
	•	toast notifications
	•	skeleton loading
	•	page transitions
	•	calendar interactions
	•	appointment selection
Animations should generally be:
	•	fast
	•	subtle
	•	purposeful
Avoid:
	•	excessive bouncing
	•	unnecessary spinning
	•	large movement
	•	animation on every element
Respect:
prefers-reduced-motion.

Dashboard
The Bookly dashboard should feel like a professional SaaS application.
Prioritize:
	•	clear navigation
	•	strong page hierarchy
	•	useful information density
	•	fast scanning
	•	excellent empty states
	•	clear actions
Dashboard should include visual hierarchy such as:
Page Header
    ↓
Primary Action
    ↓
Important Metrics
    ↓
Main Content
    ↓
Secondary Information
Do not fill empty space with meaningless widgets.
Every dashboard element must have a purpose.

Calendar
The calendar should prioritize usability over visual complexity.
It should be immediately understandable.
Use:
	•	clear time labels
	•	clear appointment blocks
	•	service/customer information
	•	employee information where useful
	•	obvious current-time indicator
	•	hover states
	•	selected states
Do not over-design the calendar.
The user should understand their schedule within seconds.

Public Booking Page
This is one of Bookly's most important screens.
It should feel significantly more polished than a normal CRUD application.
The customer journey should be obvious:
Business
↓
Service
↓
Employee
↓
Date
↓
Time
↓
Customer Details
↓
Confirmation
Minimize unnecessary friction.
Make the selected state visually obvious.
Available slots should feel inviting.
Unavailable slots should clearly communicate why they cannot be selected when appropriate.
The confirmation screen should feel rewarding and trustworthy.

Empty States
Never show a blank page.
Every empty state should explain:
	1	What is missing
	2	Why it matters
	3	What the user can do next
Example:
No appointments yet

Your upcoming bookings will appear here.

[Create appointment]
Make empty states visually polished but concise.

Loading States
Never leave users staring at an empty layout while data loads.
Use:
	•	skeletons
	•	subtle loading indicators
	•	optimistic updates where appropriate
Avoid full-page spinners unless absolutely necessary.

Error States
Errors should be:
	•	understandable
	•	actionable
	•	visually clear
	•	non-technical
Bad:
"HTTP 409 Conflict"
Better:
"This time slot was just booked by someone else. Please choose another time."
Never expose stack traces or internal errors to users.

Forms
Forms should feel simple and trustworthy.
Every input should have:
	•	label
	•	clear purpose
	•	validation
	•	helpful error message
	•	focus state
Do not validate aggressively while the user is still typing unless it improves UX.
Use appropriate input types.
Use clear success/error feedback.

Responsive Design
Design mobile-first.
The application must work properly on:
	•	mobile
	•	tablet
	•	laptop
	•	large desktop
Do not simply shrink the desktop UI.
Reconsider layouts at smaller breakpoints.
For example:
Desktop:
Sidebar | Content
Mobile:
Header
Content
Bottom navigation / menu
Public booking must be especially excellent on mobile.

Accessibility
Accessibility is mandatory.
Ensure:
	•	semantic HTML
	•	keyboard navigation
	•	visible focus states
	•	sufficient contrast
	•	labels for form fields
	•	accessible dialogs
	•	accessible dropdowns
	•	alt text where appropriate
	•	reduced motion support
Never communicate information through color alone.

Icons
Use one consistent icon library.
Do not mix unrelated icon styles.
Icons should support the UI rather than decorate it unnecessarily.
Avoid using emojis as UI icons.

Responsive Interaction
Every interactive component must define:
	•	default
	•	hover
	•	focus
	•	active
	•	disabled
	•	loading
	•	error
where applicable.

Visual Consistency
Before creating a new component, inspect existing components.
Reuse existing:
	•	buttons
	•	inputs
	•	cards
	•	modals
	•	typography
	•	spacing
	•	colors
	•	icons
Do not create five different versions of the same component.
If an existing component is inadequate, improve the shared component rather than duplicating it.

Component Architecture
Use reusable components.
Prefer:
components/
├── ui/
├── layout/
├── forms/
├── calendar/
├── appointments/
└── booking/
Avoid giant components.
A page should compose smaller components rather than contain hundreds of lines of JSX.

Tailwind
Use Tailwind consistently.
Avoid random one-off values when a design token already exists.
Prefer reusable design tokens for:
	•	colors
	•	spacing
	•	border radius
	•	shadows
	•	typography
Do not create arbitrary Tailwind classes repeatedly.

Before Implementing UI
Before writing code:
	1	Understand the page's purpose.
	2	Identify the primary user action.
	3	Identify secondary actions.
	4	Identify important information.
	5	Determine responsive behavior.
	6	Check existing components.
	7	Check the existing design system.
	8	Reuse existing patterns.
Do not immediately start writing JSX.

Before Finishing UI
Perform a visual review.
Check:
Desktop
	•	alignment
	•	spacing
	•	hierarchy
	•	typography
	•	visual balance
	•	empty space
Mobile
	•	overflow
	•	button sizes
	•	navigation
	•	text wrapping
	•	touch targets
	•	modal behavior
Interaction
	•	hover
	•	focus
	•	loading
	•	errors
	•	disabled states
	•	transitions
Accessibility
	•	keyboard navigation
	•	contrast
	•	semantic structure
Consistency
	•	colors
	•	radius
	•	shadows
	•	spacing
	•	typography
	•	icons

Anti-Patterns
Never produce:
	•	generic AI-looking dashboards
	•	excessive gradients
	•	excessive glassmorphism
	•	giant rounded cards everywhere
	•	excessive shadows
	•	meaningless decorative elements
	•	random colors
	•	inconsistent spacing
	•	inconsistent border radius
	•	tiny unreadable text
	•	desktop-only layouts
	•	placeholder UI presented as finished UI
	•	fake functionality
	•	unnecessary animations
	•	unnecessary dependencies
Do not add visual complexity simply to make the application look "fancy".

Product Quality Rule
Every screen should answer:
What is the user trying to accomplish here?
The UI should make that action obvious.
A beautiful interface that is confusing is a failure.
A simple interface that is clear, polished, and intentional is successful.

Bookly Quality Bar
Before declaring a frontend task complete, ask:
"Would this look credible if Bookly were launched tomorrow as a real SaaS company?"
If the answer is no, improve it.
The target is not:
"Looks good for a student project."
The target is:
"Looks like a real product."
