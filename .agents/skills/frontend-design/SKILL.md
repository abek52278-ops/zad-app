---
name: frontend-design
description: Guidance for distinctive, intentional visual design when building new UI or reshaping an existing one. Helps with aesthetic direction, typography, and making choices that don't read as templated defaults.
license: Complete terms in LICENSE.txt
---

# Frontend Design

Approach this as the design lead at a small studio known for giving every client a visual identity that could not be mistaken for anyone else's. This client has already rejected proposals that felt templated, and is paying for a distinctive point of view: make deliberate, opinionated choices about palette, typography, and layout that are specific to this brief, and take one real aesthetic risk you can justify.

## Ground it in the subject

If the brief does not pin down what the product or subject is, pin it yourself before designing: name one concrete subject, its audience, and the page's single job, and state your choice. If there's any information in your memory about the human's preferences, context about what they're building, or designs you've made before – use that as a hint. The subject's own world, its materials, instruments, artifacts, and vernacular, is where distinctive choices come from. Build with the brief's real content and subject matter throughout.

## Design principles

For web designs, the hero is a thesis. Open with the most characteristic thing in the subject's world, in whatever form makes sense for it: a headline, an image, an animation, a live demo, an interactive moment. Be deliberate with your choice: a big number with a small label, supporting stats, and a gradient accent is the template answer, only use if that's truly the best option.

Typography carries the personality of the page. Pair the display and body faces deliberately, not the same families you would reach for on any other project, and set a clear type scale with intentional weights, widths, and spacing. Make the type treatment itself a memorable part of the design, not a neutral delivery vehicle for the content.

Structure is information. Structural devices, numbering, eyebrows, dividers, labels, should encode something true about the content, not decorate it. Many generic designs use numbered markers (01 / 02 / 03), but that's only appropriate if the content actually is a sequence - like a real process or a typed timeline where order carries information the reader needs. Question if choices like numbered markers actually make sense before incorporating them.

Leverage motion deliberately. Think about where and if animation can serve the subject: a page-load sequence, a scroll-triggered reveal, hover micro-interactions, ambient atmosphere. An orchestrated moment usually lands harder than scattered effects; choose what the direction calls for. However, sometimes less is more, and extra animation contributes to the feeling that the design is AI-generated.

Match complexity to the vision. Maximalist directions need elaborate execution; minimal directions need precision in spacing, type, and detail. Elegance is executing the chosen vision well.

Consider written content carefully. Often a design brief may not contain real content, and it's up to you to come up with copy. Copy can make a design feel as templated as the design itself. See the below section on writing for more guidance.

## Process: brainstorm, explore, plan, critique, build, critique again

For calibration: AI-generated design right now clusters around three looks: (1) a warm cream background (near #F4F1EA) with a high-contrast serif display and a terracotta accent; (2) a near-black background with a single bright acid-green or vermilion accent; (3) a broadsheet-style layout with hairline rules, zero border-radius, and dense newspaper-like columns. All three are legitimate for some briefs, but they are defaults rather than choices, and they appear regardless of subject. Where the brief pins down a visual direction, follow it exactly — the brief's own words always win, including when it asks for one of these looks. Where it leaves an axis free, don't spend that freedom on one of these defaults. Just like a human designer who's hired, there's often a careful balance between doing what you're good at and taking each project as a chance to experiment and learn.

Work in two passes. First, brainstorm a short design plan based on the human's design brief: create a compact token system with color, type, layout, and signature. Color: describe the palette as 4–6 named hex values. Type: the typefaces for 2+ roles (a characterful display face that's used with restraint, a complementary body face, and a utility face for captions or data if needed). Layout: a layout concept, using one-sentence prose descriptions and ASCII wireframes to ideate and compare. Signature: the single unique element this page will be remembered by that embodies the brief in an appropriate way.

Then review that plan against the brief before building: if any part of it reads like the generic default you would produce for any similar page (work through a similar prompt to see if you arrive somewhere similar) rather than a choice made for this specific brief — revise that part, say what you changed and why. Only after you've confirmed the relative uniqueness of your design plan should you start to write the code, following the revised plan exactly and deriving every color and type decision from it.

When writing the code, be careful of structuring your CSS selector specificities. It's easy to generate CSS classes that cancel each other out (especially with a type-based selector like .section and a element-based selector like .cta). This can happen often with paddings/margins between sections.

Try to do a lot of this planning and iteration in your thinking, and only show ideas to the user when you have higher confidence it'll delight them.

## Restraint and self-critique

Spend your boldness in one place. Let the signature element be the one memorable thing, keep everything around it quiet and disciplined, and cut any decoration that does not serve the brief. Not taking a risk can be a risk itself! Build to a quality floor without announcing it: responsive down to mobile, visible keyboard focus, reduced motion respected. Critique your own work as you build, taking screenshots if your environment supports it – a picture is worth 1000 tokens. Consider Chanel's advice: before leaving the house, take a look in the mirror and remove one accessory. Human creators have memory and always try to do something new, so if you have a space to quickly jot down notes about what you've tried, it can help you in future passes.

## More on writing in design

Words appear in a design for one reason: to make it easier to understand, and therefore easier to use. They are design material, not decoration. Bring the same intentionality to copy that you would bring to spacing and color. Before writing anything, ask what the design needs to say, and how it can best be said to help the person navigate the experience.

Write from the end user's side of the screen. Name things by what people control and recognize, never by how the system is built. A person manages notifications, not webhook config. Describe what something does in plain terms rather than selling it. Being specific is always better than being clever.

Use active voice as default. A control should say exactly what happens when it's used: "Save changes," not "Submit." An action keeps the same name through the whole flow, so the button that says "Publish" produces a toast that says "Published." The vocabulary of an interface is the signposting for someone navigating the product. Cohesion and consistency are how people learn their way around.

Treat failure and emptiness as moments for direction, not mood. Explain what went wrong and how to fix it, in the interface's voice rather than a person's. Errors don't apologize, and they are never vague about what happened. An empty screen is an invitation to act.

Keep the register conversational and tuned: plain verbs, sentence case, no filler, with tone matched to the brand and the audience. Let each element do exactly one job. A label labels, an example demonstrates, and nothing quietly does double duty.
name	ui-ux-pro-max
description	UI/UX design intelligence for web and mobile. Searchable local database with 84 styles, 192 color palettes, 74 font pairings, 192 product types, 98 UX guidelines, 104 icon entries, 16 GSAP motion presets, and 25 chart types across 22 stacks (React, Next.js, Vue, Nuxt, Svelte, Astro, SwiftUI, React Native, Flutter, Tailwind, shadcn/ui, Jetpack Compose, Angular, Laravel, JavaFX, WPF, WinUI, Avalonia, Uno Platform, UWP, Three.js, and HTML/CSS). Use when designing, building, or reviewing UI: pages, components, color schemes, typography, layout, accessibility, animation, or data visualization.
UI/UX Pro Max - Design Intelligence
Searchable database of UI/UX design rules with priority-based recommendations: 84 styles, 192 color palettes, 74 font pairings, 192 product types with reasoning rules, 98 UX guidelines, 104 icon entries, 16 GSAP motion presets, and 25 chart types across 22 technology stacks.

When to Apply
Use this Skill when the task involves UI structure, visual design decisions, interaction patterns, or user experience quality control: designing new pages, creating/refactoring UI components, choosing color/typography/spacing/layout systems, reviewing UI for UX/accessibility/consistency, implementing navigation/animation/responsive behavior, or improving perceived quality and usability.

Skip it for pure backend logic, API/database design, non-visual performance work, infrastructure/DevOps, or non-visual scripts — unless the task changes how something looks, feels, moves, or is interacted with.

Rule Categories by Priority
Follow priority 1→10 to decide which category to focus on first; use --domain <Domain> to query full details. The full rule text for every category lives in references/quick-reference.md — read it on demand rather than loading it every time.

Priority	Category	Impact	Domain	Key Checks (Must Have)	Anti-Patterns (Avoid)
1	Accessibility	CRITICAL	ux	Contrast 4.5:1, Alt text, Keyboard nav, Aria-labels	Removing focus rings, Icon-only buttons without labels
2	Touch & Interaction	CRITICAL	ux	Min size 44×44px, 8px+ spacing, Loading feedback	Reliance on hover only, Instant state changes (0ms)
3	Performance	HIGH	ux	WebP/AVIF, Lazy loading, Reserve space (CLS < 0.1)	Layout thrashing, Cumulative Layout Shift
4	Style Selection	HIGH	style, product	Match product type, Consistency, SVG icons (no emoji)	Mixing flat & skeuomorphic randomly, Emoji as icons
5	Layout & Responsive	HIGH	ux	Mobile-first breakpoints, Viewport meta, No horizontal scroll	Horizontal scroll, Fixed px container widths, Disable zoom
6	Typography & Color	MEDIUM	typography, color	Base 16px, Line-height 1.5, Semantic color tokens	Text < 12px body, Gray-on-gray, Raw hex in components
7	Animation	MEDIUM	ux, gsap	Duration 150–300ms, Motion conveys meaning, Spatial continuity	Decorative-only animation, Animating width/height, No reduced-motion
8	Forms & Feedback	MEDIUM	ux	Visible labels, Error near field, Helper text, Progressive disclosure	Placeholder-only label, Errors only at top, Overwhelm upfront
9	Navigation Patterns	HIGH	ux	Predictable back, Bottom nav ≤5, Deep linking	Overloaded nav, Broken back behavior, No deep links
10	Charts & Data	LOW	chart	Legends, Tooltips, Accessible colors	Relying on color alone to convey meaning
For the full rule list per category (all ~98 UX guidelines with rationale), read references/quick-reference.md. For app-specific polish rules (icons, touch feedback, dark mode contrast, safe areas) and the canonical pre-delivery checklist, read references/pro-rules.md.

Running the search tool
The search script lives inside this skill's own directory, not the project directory. Always invoke it by its full path — do not assume a particular working directory:

python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "<query>" --domain <domain>
If python is not found, try python3, then py -3. Requires Python 3.x, no external dependencies (see README for install instructions if Python is missing).

Workflow
Step 1: Analyze User Requirements
Extract from the user request:

Product type: SaaS, e-commerce, portfolio, dashboard, entertainment, tool, productivity, or hybrid
Target audience & context: age group, usage context (commute, leisure, work)
Style keywords: playful, vibrant, minimal, dark mode, content-first, immersive, etc.
Stack: detect from the project — check package.json deps (react/next/vue/svelte/nuxt/@angular), pubspec.yaml (Flutter), *.xcodeproj/Package.swift (SwiftUI), composer.json (Laravel), or React Native markers (app.json + react-native dep). If nothing is detectable, ask the user or default to html-tailwind. Never assume a stack — a hardcoded default silently misroutes every recommendation.
Step 2: Generate Design System (REQUIRED for new pages/projects)
Always start with --design-system to get comprehensive recommendations with reasoning:

python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "<product_type> <industry> <keywords>" --design-system [-p "Project Name"]
This searches product/style/color/landing/typography domains in parallel, applies reasoning rules from ui-reasoning.csv, and returns pattern, style, colors, typography, effects, and anti-patterns to avoid.

Example:

python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "beauty spa wellness service" --design-system -p "Serenity Spa"
Step 2b: Persist Design System (Master + Overrides Pattern)
To save the design system for retrieval across sessions, add --persist and always pass --output-dir pointed at the project root — without it, files are written relative to whatever directory the tool happens to run from:

python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "<query>" --design-system --persist -p "Project Name" --output-dir "<project-root>"
This creates:

design-system/<project-slug>/MASTER.md — Global Source of Truth
design-system/<project-slug>/pages/ — Folder for page-specific overrides
With a page-specific override, add --page "dashboard" to also create design-system/<project-slug>/pages/dashboard.md.

If design-system/<project-slug>/MASTER.md already exists, --persist skips writing and leaves it untouched unless you also pass --force — check whether it exists first (and read it) before regenerating, so you don't silently discard prior decisions the user or a teammate made.

Retrieval when building a specific page:

Read design-system/<project-slug>/MASTER.md
Check if design-system/<project-slug>/pages/<page-name>.md exists — if so, its rules override Master
Otherwise use Master rules exclusively
Step 2c: Design Dials (optional)
Three optional 1-10 sliders that tune --design-system output without changing your query. Add any combination of them to the same command:

python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "<query>" --design-system --variance <1-10> --motion <1-10> --density <1-10>
Dial	Low (1-3)	Mid (4-7)	High (8-10)
--variance	Centered / minimal (biases toward Minimalism-style categories)	Balanced / modern	Bold / asymmetric (biases toward Brutalism, Bento Grids)
--motion	Subtle micro-interactions	Standard scroll/stagger motion	Complex choreography (pin, Flip, SplitText)
--density	Spacious (24-96px spacing scale)	Standard (16-64px, current default)	Dense/dashboard (8-32px spacing scale)
--motion attaches a ready-to-use GSAP snippet (with framework notes, Do/Don't, and performance notes) pulled from --domain gsap, matched to the resolved tier (Subtle/Standard/Complex).
--density overrides the --space-* CSS variable table in the ASCII/markdown/MASTER.md output — use it for dashboards (high) vs. marketing pages (low) without hand-editing tokens.
Leaving a dial unset keeps that part of the output exactly as it was before (no behavior change).
Example:

python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "internal analytics dashboard" --design-system --variance 8 --motion 7 --density 8 -p "Ops Console"
Step 3: Supplement with Detailed Searches (as needed)
python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "<keyword>" --domain <domain> [-n <max_results>]
Need	Domain	Example
Product type patterns	product	--domain product "entertainment social"
More style options	style	--domain style "glassmorphism dark"
Color palettes	color	--domain color "entertainment vibrant"
Font pairings	typography	--domain typography "playful modern"
Individual Google Fonts	google-fonts	--domain google-fonts "sans serif popular variable"
Chart recommendations	chart	--domain chart "real-time dashboard"
UX best practices	ux	--domain ux "animation accessibility"
Landing page structure	landing	--domain landing "hero social-proof"
Icon recommendations	icons	--domain icons "navigation outline"
GSAP animation presets	gsap	--domain gsap "scroll reveal stagger"
React/Next.js performance	react	--domain react "rerender memo list"
App/native interface guidelines	web	--domain web "accessibilityLabel touch safe-areas"
Domain is auto-detected from the query if --domain is omitted — but auto-detection can misroute overlapping terms (e.g. "font" matches both typography and google-fonts). If results look off-topic, pass --domain explicitly.

Step 4: Stack Guidelines
python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "<keyword>" --stack <stack>
Available stacks: react, nextjs, vue, svelte, astro, nuxtjs, nuxt-ui, angular, laravel, swiftui, react-native, flutter, jetpack-compose, html-tailwind, shadcn, threejs, javafx, wpf, winui, avalonia, uno, uwp. Use the stack detected in Step 1.

If a search returns 0 results
Do not fabricate output. Instead:

Retry once with broader or differently-worded keywords (try product + style separately rather than combined).
If still empty, fall back to the priority table above and say explicitly to the user that this recommendation came from the built-in defaults, not a database match (e.g. "no palette match for X, using general SaaS defaults").
Never present a 0-result search as if it returned data.
Example Workflow
User request: "Make an AI search homepage." (stack detected as Next.js from package.json)

# Step 2: design system
python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "AI search tool modern minimal" --design-system -p "AI Search"

# Step 3: supplement
python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "search loading animation" --domain ux

# Step 4: stack guidelines
python "${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/scripts/search.py" "suspense streaming bundle" --stack nextjs
Then synthesize the design system + detailed searches and implement.

Output Formats
--design-system supports -f ascii (default, terminal display), -f markdown (documentation), and --json (machine-readable, includes the raw design system dict plus persistence status).

Tips for Better Results
Use multi-dimensional keywords — combine product + industry + tone + density: "entertainment social vibrant content-dense", not just "app"
Try different phrasings for the same need: "playful neon" → "vibrant dark" → "content-first minimal"
Use --design-system first for full recommendations, then --domain to deep-dive any dimension you're unsure about
Pass the detected stack explicitly for implementation-specific guidance
Problem	What to Do
Can't decide on style/color	Re-run --design-system with different keywords
Dark mode contrast issues	references/quick-reference.md §6: color-dark-mode + color-accessible-pairs
Animations feel unnatural	references/quick-reference.md §7: spring-physics + easing + exit-faster-than-enter
Form UX is poor	references/quick-reference.md §8: inline-validation + error-clarity + focus-management
Navigation feels confusing	references/quick-reference.md §9: nav-hierarchy + bottom-nav-limit + back-behavior
Layout breaks on small screens	references/quick-reference.md §5: mobile-first + breakpoint-consistency
Performance / jank	references/quick-reference.md §3: virtualize-lists + main-thread-budget + debounce-throttle
Before Delivering App UI
Read references/pro-rules.md and run through its canonical Pre-Delivery Checklist. It covers icon/visual-element discipline, interaction feedback, light/dark contrast, safe-area layout, and accessibility — scoped to native/mobile app UI (iOS/Android/React Native/Flutter).