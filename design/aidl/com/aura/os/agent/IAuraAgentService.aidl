// DESIGN SKETCH ONLY: intentionally outside Soong module source roots.
// DO NOT wire this interface to SystemServer until trusted caller validation,
// typed parcelables, approval grants and SELinux have been implemented.
package com.aura.os.agent;

import com.aura.os.agent.AuraActionRequest;
import com.aura.os.agent.AuraActionProposal;
import com.aura.os.agent.AuraExecutionRequest;
import com.aura.os.agent.AuraExecutionResult;

interface IAuraAgentService {
    AuraActionProposal proposeAction(in AuraActionRequest request);
    AuraExecutionResult executeAction(in AuraExecutionRequest request);
    AuraActionProposal getProposalStatus(String proposalId);
}
