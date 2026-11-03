const newman = require("/tmp/newman/node_modules/newman");
const path = require("path");
const collection = require(path.join("/tmp", process.argv[2]));
const fs = require("fs");
const scenario = process.argv[3] || "";
const invitation = fs.existsSync("/tmp/invitation-runtime.json")
  ? JSON.parse(fs.readFileSync("/tmp/invitation-runtime.json", "utf8")) : {};
if (scenario) {
  const prefixes = {main:["00", "01"], retry:["02"], link:["03"]}[scenario];
  if (!prefixes) throw new Error("Unknown fixture scenario");
  collection.item = collection.item.filter(item => prefixes.some(prefix => item.name.startsWith(prefix)));
}
const values = Object.entries({
  baseUrl: process.env.EP06_BASE_URL,
  accessTokenA: process.env.EP06_TOKEN_A,
  accessTokenB: process.env.EP06_TOKEN_B,
  accessTokenC: process.env.EP06_TOKEN_C,
  allowUnfriendTest: "true", allowChatSessionTest: "true", allowChatMessageTest: "true",
  allowChatImageTest: "true", imagePath: "/tmp/chat-image.png",
  allowFriendInvitationMutations: scenario === "main" ? "true" : "false",
  allowRetryScenario: scenario === "retry" ? "true" : "false",
  allowEmailLinkScenario: scenario === "link" ? "true" : "false",
  friendEmailA: process.env.EP06_EMAIL_A || "", friendEmailB: process.env.EP06_EMAIL_B || "",
  allowOutfitSharingTest: "true", friendAccountA: process.env.EP06_ACCOUNT_A || "",
  friendAccountB: process.env.EP06_ACCOUNT_B || "", variantId: process.env.EP06_OUTFIT_VARIANT || "",
  ...invitation
}).map(([key,value]) => ({key,value,enabled:true}));
newman.run({collection, environment:{name:"ephemeral EP06 fixture",values}, reporters:[], bail:true}, (error, summary) => {
  const failures = summary && summary.run ? summary.run.failures : [];
  const safe = {
    collection: collection.info.name,
    scenario: scenario || undefined,
    requests: summary && summary.run ? summary.run.stats.requests.total : 0,
    assertions: summary && summary.run ? summary.run.stats.assertions.total : 0,
    failures: failures.map(f => {
      const safe = {source: f.source && f.source.name || "request", type: f.error && f.error.name || "error"};
      if (safe.type === "SyntaxError") {
        let diagnostic = String(f.error.message || "Syntax error");
        [process.env.EP06_TOKEN_A,process.env.EP06_TOKEN_B,process.env.EP06_TOKEN_C,invitation.invitationEmailToken].filter(Boolean)
          .forEach(token => diagnostic = diagnostic.split(token).join("[REDACTED]"));
        safe.syntax = diagnostic.slice(0,160);
      }
      return safe;
    }),
    executionError: !!error
  };
  process.stdout.write("EP06_NEWMAN_RESULT=" + JSON.stringify(safe) + "\n");
  process.exitCode = error || failures.length ? 1 : 0;
});
